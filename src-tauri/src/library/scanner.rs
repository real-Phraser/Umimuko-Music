use crate::library::artwork::{fnv1a_hex, save_cover_to_cache};
use crate::library::db::{Database, TrackRecord};
use crate::library::metadata::read_metadata;
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

pub const SUPPORTED_EXTENSIONS: &[&str] = &["mp3", "flac", "m4a", "aac", "ogg", "wav", "alac"];

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ScanProgress {
    pub scanned_files: usize,
    pub total_files: usize,
    pub imported_tracks: usize,
    pub current_path: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct ScanResult {
    pub total_files: usize,
    pub imported_tracks: usize,
    pub skipped_files: usize,
    pub errors: Vec<String>,
}

/// Checks whether a path has one of the supported audio extensions (case-insensitive).
pub fn is_supported_audio_file(path: &Path) -> bool {
    path.extension()
        .and_then(|e| e.to_str())
        .map(|ext| {
            let lower = ext.to_ascii_lowercase();
            SUPPORTED_EXTENSIONS.contains(&lower.as_str())
        })
        .unwrap_or(false)
}

/// Collects all supported audio files recursively from the given root folders.
pub fn collect_audio_files(folders: &[PathBuf]) -> Vec<PathBuf> {
    let mut files = Vec::new();
    let mut stack: Vec<PathBuf> = folders.to_vec();
    while let Some(dir) = stack.pop() {
        let Ok(entries) = std::fs::read_dir(&dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                stack.push(path);
            } else if path.is_file() && is_supported_audio_file(&path) {
                files.push(path);
            }
        }
    }
    files.sort();
    files.dedup();
    files
}

fn unix_now_secs() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

fn modified_unix_secs(meta: &std::fs::Metadata) -> i64 {
    meta.modified()
        .ok()
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

/// Recursively scans `folders`, reads tags via `lofty`, caches cover art in `cache_root/covers-v1/`,
/// upserts tracks into `db`, and invokes `on_progress` for each discovered file.
pub fn scan_folders<F>(
    db: &Database,
    folders: &[PathBuf],
    cache_root: &Path,
    mut on_progress: F,
) -> ScanResult
where
    F: FnMut(ScanProgress),
{
    let files = collect_audio_files(folders);
    let total_files = files.len();
    let mut result = ScanResult {
        total_files,
        ..Default::default()
    };

    let now = unix_now_secs();
    for (idx, file_path) in files.iter().enumerate() {
        let path_str = file_path.to_string_lossy().to_string();
        let fs_meta = match std::fs::metadata(file_path) {
            Ok(m) => m,
            Err(e) => {
                result.skipped_files += 1;
                result.errors.push(format!("{path_str}: {e}"));
                on_progress(ScanProgress {
                    scanned_files: idx + 1,
                    total_files,
                    imported_tracks: result.imported_tracks,
                    current_path: path_str,
                });
                continue;
            }
        };

        let file_size = fs_meta.len() as i64;
        let modified_at = modified_unix_secs(&fs_meta);

        // Skip unchanged files already in DB
        if let Ok(Some(existing)) = db.get_track_by_path(&path_str) {
            if existing.file_size == file_size && existing.modified_at == modified_at {
                result.skipped_files += 1;
                on_progress(ScanProgress {
                    scanned_files: idx + 1,
                    total_files,
                    imported_tracks: result.imported_tracks,
                    current_path: path_str,
                });
                continue;
            }
        }

        match read_metadata(file_path) {
            Ok(meta) => {
                let (artwork_path, accent_color) = if let Some(ref cover_bytes) = meta.cover_art {
                    match save_cover_to_cache(cache_root, cover_bytes) {
                        Ok((p, hex)) => (Some(p.to_string_lossy().to_string()), Some(hex)),
                        Err(_) => (None, None),
                    }
                } else {
                    (None, None)
                };

                let track = TrackRecord {
                    id: format!("trk_{}", fnv1a_hex(path_str.as_bytes())),
                    path: path_str.clone(),
                    title: meta.title,
                    artist: meta.artist,
                    album: meta.album,
                    album_artist: meta.album_artist,
                    genre: meta.genre,
                    year: meta.year,
                    track_num: meta.track_num,
                    duration_ms: meta.duration_ms,
                    artwork_path,
                    accent_color,
                    added_at: now,
                    file_size,
                    modified_at,
                };

                if let Err(e) = db.upsert_track(&track) {
                    result.errors.push(format!("{path_str}: {e}"));
                } else {
                    result.imported_tracks += 1;
                }
            }
            Err(e) => {
                result.skipped_files += 1;
                result.errors.push(format!("{path_str}: {e}"));
            }
        }

        on_progress(ScanProgress {
            scanned_files: idx + 1,
            total_files,
            imported_tracks: result.imported_tracks,
            current_path: path_str,
        });
    }

    result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_supported_extensions() {
        for ext in ["mp3", "FLAC", "m4a", "aac", "ogg", "wav", "alac"] {
            let p = PathBuf::from(format!("/music/song.{ext}"));
            assert!(is_supported_audio_file(&p), "expected {ext} to be supported");
        }
        assert!(!is_supported_audio_file(Path::new("/music/cover.png")));
        assert!(!is_supported_audio_file(Path::new("/music/lyrics.lrc")));
    }

    #[test]
    fn test_collect_audio_files_recursive() {
        let tmp = std::env::temp_dir().join(format!("umimuko_scan_test_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&tmp);
        let sub = tmp.join("Artist/Album");
        std::fs::create_dir_all(&sub).unwrap();
        std::fs::write(sub.join("01.flac"), b"dummy").unwrap();
        std::fs::write(sub.join("02.mp3"), b"dummy").unwrap();
        std::fs::write(sub.join("cover.jpg"), b"dummy").unwrap();

        let files = collect_audio_files(&[tmp.clone()]);
        assert_eq!(files.len(), 2);

        let _ = std::fs::remove_dir_all(&tmp);
    }
}
