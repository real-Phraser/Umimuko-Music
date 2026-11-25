use crate::library::artwork::fnv1a_hex;
use crate::library::db::TrackRecord;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use std::fs::File;
use std::io::Read;
use std::path::Path;

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum DuplicateMatchKind {
    ExactPath,
    ExactFileHash,
    MetadataAndDuration,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct DuplicateGroup {
    pub key: String,
    pub kind: DuplicateMatchKind,
    pub tracks: Vec<TrackRecord>,
}

pub struct DuplicateDetector;

impl DuplicateDetector {
    /// Detects duplicate tracks using:
    /// 1. Exact normalized path duplicates
    /// 2. Exact file content hash (for existing files with matching file size)
    /// 3. Normalized metadata (title + artist) + duration within tolerance (`duration_tolerance_ms`)
    pub fn find_duplicates(
        tracks: &[TrackRecord],
        duration_tolerance_ms: i64,
    ) -> Vec<DuplicateGroup> {
        let mut groups = Vec::new();
        let mut grouped_ids: HashSet<String> = HashSet::new();

        // 1. Exact normalized path
        let mut by_path: HashMap<String, Vec<TrackRecord>> = HashMap::new();
        for t in tracks {
            let norm = normalize_path(&t.path);
            by_path.entry(norm).or_default().push(t.clone());
        }
        for (key, list) in by_path {
            if list.len() > 1 {
                for t in &list {
                    grouped_ids.insert(t.id.clone());
                }
                groups.push(DuplicateGroup {
                    key: format!("path:{key}"),
                    kind: DuplicateMatchKind::ExactPath,
                    tracks: list,
                });
            }
        }

        // 2. Exact file hash (only hash files that share the same file_size > 0 and exist on disk)
        let mut by_size: HashMap<i64, Vec<&TrackRecord>> = HashMap::new();
        for t in tracks {
            if !grouped_ids.contains(&t.id) && t.file_size > 0 {
                by_size.entry(t.file_size).or_default().push(t);
            }
        }
        let mut by_hash: HashMap<String, Vec<TrackRecord>> = HashMap::new();
        for (_size, candidates) in by_size {
            if candidates.len() < 2 {
                continue;
            }
            for t in candidates {
                if let Some(hash) = hash_file(Path::new(&t.path)) {
                    by_hash.entry(hash).or_default().push(t.clone());
                }
            }
        }
        for (hash, list) in by_hash {
            if list.len() > 1 {
                for t in &list {
                    grouped_ids.insert(t.id.clone());
                }
                groups.push(DuplicateGroup {
                    key: format!("hash:{hash}"),
                    kind: DuplicateMatchKind::ExactFileHash,
                    tracks: list,
                });
            }
        }

        // 3. Metadata (normalized title + artist) + duration within tolerance
        let mut by_meta: HashMap<(String, String), Vec<TrackRecord>> = HashMap::new();
        for t in tracks {
            if grouped_ids.contains(&t.id) {
                continue;
            }
            let title = normalize_text(&t.title);
            let artist = normalize_text(&t.artist);
            if title.is_empty() {
                continue;
            }
            by_meta.entry((title, artist)).or_default().push(t.clone());
        }

        for ((title, artist), mut list) in by_meta {
            if list.len() < 2 {
                continue;
            }
            list.sort_by_key(|t| t.duration_ms);

            // Cluster tracks whose durations are within `duration_tolerance_ms` of the cluster anchor
            let mut cluster: Vec<TrackRecord> = Vec::new();
            let mut anchor_dur: i64 = 0;
            for t in list {
                if cluster.is_empty() {
                    anchor_dur = t.duration_ms;
                    cluster.push(t);
                } else if (t.duration_ms - anchor_dur).abs() <= duration_tolerance_ms {
                    cluster.push(t);
                } else {
                    if cluster.len() > 1 {
                        groups.push(DuplicateGroup {
                            key: format!("meta:{artist}::{title}::{anchor_dur}"),
                            kind: DuplicateMatchKind::MetadataAndDuration,
                            tracks: cluster,
                        });
                    }
                    anchor_dur = t.duration_ms;
                    cluster = vec![t];
                }
            }
            if cluster.len() > 1 {
                groups.push(DuplicateGroup {
                    key: format!("meta:{artist}::{title}::{anchor_dur}"),
                    kind: DuplicateMatchKind::MetadataAndDuration,
                    tracks: cluster,
                });
            }
        }

        groups.sort_by(|a, b| a.key.cmp(&b.key));
        groups
    }
}

fn normalize_path(path: &str) -> String {
    path.trim().replace('\\', "/").to_ascii_lowercase()
}

fn normalize_text(s: &str) -> String {
    s.trim()
        .to_ascii_lowercase()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
}

fn hash_file(path: &Path) -> Option<String> {
    let mut file = File::open(path).ok()?;
    let mut buf = Vec::new();
    file.read_to_end(&mut buf).ok()?;
    Some(fnv1a_hex(&buf))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_track(id: &str, path: &str, title: &str, artist: &str, duration_ms: i64, size: i64) -> TrackRecord {
        TrackRecord {
            id: id.to_string(),
            path: path.to_string(),
            title: title.to_string(),
            artist: artist.to_string(),
            album: "Album".to_string(),
            album_artist: artist.to_string(),
            genre: "Pop".to_string(),
            year: Some(2022),
            track_num: Some(1),
            duration_ms,
            artwork_path: None,
            accent_color: None,
            added_at: 0,
            file_size: size,
            modified_at: 0,
        }
    }

    #[test]
    fn test_detects_exact_path_duplicates() {
        let tracks = vec![
            make_track("1", "/Music/Song.mp3", "Song", "Artist", 180_000, 100),
            make_track("2", "/music/song.mp3", "Song", "Artist", 180_000, 100),
        ];
        let groups = DuplicateDetector::find_duplicates(&tracks, 2000);
        assert_eq!(groups.len(), 1);
        assert_eq!(groups[0].kind, DuplicateMatchKind::ExactPath);
        assert_eq!(groups[0].tracks.len(), 2);
    }

    #[test]
    fn test_detects_exact_file_hash_duplicates() {
        let tmp = std::env::temp_dir().join(format!("umimuko_dup_test_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&tmp);
        std::fs::create_dir_all(&tmp).unwrap();

        let f1 = tmp.join("a.mp3");
        let f2 = tmp.join("b.mp3");
        let f3 = tmp.join("c.mp3");
        std::fs::write(&f1, b"identical-audio-payload-1234").unwrap();
        std::fs::write(&f2, b"identical-audio-payload-1234").unwrap();
        std::fs::write(&f3, b"different-audio-payload-9999").unwrap();

        let tracks = vec![
            make_track("1", f1.to_str().unwrap(), "A", "X", 100_000, 28),
            make_track("2", f2.to_str().unwrap(), "B", "Y", 100_000, 28),
            make_track("3", f3.to_str().unwrap(), "C", "Z", 100_000, 28),
        ];

        let groups = DuplicateDetector::find_duplicates(&tracks, 2000);
        assert_eq!(groups.len(), 1);
        assert_eq!(groups[0].kind, DuplicateMatchKind::ExactFileHash);
        assert_eq!(groups[0].tracks.len(), 2);

        let _ = std::fs::remove_dir_all(&tmp);
    }

    #[test]
    fn test_detects_metadata_and_duration_duplicates() {
        let tracks = vec![
            make_track("1", "/a/1.flac", "Starlight", "Muse", 240_000, 30_000_000),
            make_track("2", "/b/2.mp3", "  starlight ", "MUSE", 241_200, 8_000_000),
            // Same title/artist but different duration (e.g. live version) -> should NOT group
            make_track("3", "/c/3.mp3", "Starlight", "Muse", 310_000, 10_000_000),
        ];
        let groups = DuplicateDetector::find_duplicates(&tracks, 2000);
        assert_eq!(groups.len(), 1);
        assert_eq!(groups[0].kind, DuplicateMatchKind::MetadataAndDuration);
        assert_eq!(groups[0].tracks.len(), 2);
    }
}
