use crate::library::artwork::fnv1a_hex;
use crate::library::db::{Database, DbResult, EqPresetRecord, TrackRecord};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::{Path, PathBuf};

const MIGRATION_FLAG_KEY: &str = "migration.v0261.completed";

#[derive(Debug, Clone, Serialize, Deserialize, Default, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct MigrationReport {
    pub already_migrated: bool,
    pub legacy_dir_found: bool,
    pub folders_imported: usize,
    pub tracks_imported: usize,
    pub playlists_imported: usize,
    pub liked_imported: usize,
    pub eq_presets_imported: usize,
    pub play_history_imported: usize,
    pub settings_imported: usize,
}

/// Resolves the legacy `v0.26.1` application data directory:
/// - Windows: `%LOCALAPPDATA%\PixelPlayerDesktop`
/// - macOS: `~/Library/Application Support/Umimuko Music`
/// - Linux: `$XDG_DATA_HOME/umimuko-music` (or `~/.local/share/umimuko-music`)
pub fn legacy_data_dir() -> Option<PathBuf> {
    #[cfg(target_os = "windows")]
    {
        return dirs::data_local_dir().map(|p| p.join("PixelPlayerDesktop"));
    }
    #[cfg(target_os = "macos")]
    {
        return dirs::data_dir().map(|p| p.join("Umimuko Music"));
    }
    #[cfg(all(not(target_os = "windows"), not(target_os = "macos")))]
    {
        return dirs::data_dir().map(|p| p.join("umimuko-music"));
    }
}

/// Runs the one-time legacy `v0.26.1` migration from the platform default legacy directory.
pub fn migrate_legacy_v0261(db: &Database) -> DbResult<MigrationReport> {
    let Some(dir) = legacy_data_dir() else {
        return Ok(MigrationReport::default());
    };
    migrate_from_legacy_dir(db, &dir)
}

/// Idempotently imports `folders-v1.properties`, `library-v1.properties`, `home-v1/*`,
/// `eq-presets-v1.json`, and `listening-stats-v1.json` from `legacy_dir` into `db`.
pub fn migrate_from_legacy_dir(db: &Database, legacy_dir: &Path) -> DbResult<MigrationReport> {
    if db.get_setting(MIGRATION_FLAG_KEY)?.as_deref() == Some("true") {
        return Ok(MigrationReport {
            already_migrated: true,
            legacy_dir_found: legacy_dir.exists(),
            ..Default::default()
        });
    }

    if !legacy_dir.exists() {
        db.set_setting(MIGRATION_FLAG_KEY, "true")?;
        return Ok(MigrationReport {
            already_migrated: false,
            legacy_dir_found: false,
            ..Default::default()
        });
    }

    let mut report = MigrationReport {
        already_migrated: false,
        legacy_dir_found: true,
        ..Default::default()
    };

    // 1. folders-v1.properties
    let folders_file = legacy_dir.join("folders-v1.properties");
    if folders_file.exists() {
        if let Ok(content) = std::fs::read_to_string(&folders_file) {
            let props = parse_properties(&content);
            let mut paths = Vec::new();
            if let Some(joined) = props.get("folders") {
                for part in joined.split([';', '|', '\n']) {
                    let trimmed = part.trim();
                    if !trimmed.is_empty() {
                        paths.push(trimmed.to_string());
                    }
                }
            }
            for (k, v) in &props {
                if k.starts_with("folder.") || k.starts_with("path.") {
                    let trimmed = v.trim();
                    if !trimmed.is_empty() {
                        paths.push(trimmed.to_string());
                    }
                }
            }
            for (idx, p) in paths.into_iter().enumerate() {
                db.add_folder(&p, 1_700_000_000 + idx as i64)?;
                report.folders_imported += 1;
            }
        }
    }

    // 2. library-v1.properties
    let library_file = legacy_dir.join("library-v1.properties");
    if library_file.exists() {
        if let Ok(content) = std::fs::read_to_string(&library_file) {
            let props = parse_properties(&content);

            // Group `track.<id>.<field>` entries if present
            let mut track_fields: HashMap<String, HashMap<String, String>> = HashMap::new();
            for (k, v) in &props {
                if let Some(rest) = k.strip_prefix("track.") {
                    if let Some((tid, field)) = rest.split_once('.') {
                        track_fields
                            .entry(tid.to_string())
                            .or_default()
                            .insert(field.to_string(), v.clone());
                    }
                } else if k != "liked" && k != "likedTracks" {
                    db.set_setting(&format!("legacy.{k}"), v)?;
                    report.settings_imported += 1;
                }
            }

            for (tid, fields) in track_fields {
                if let Some(path) = fields.get("path") {
                    let record = TrackRecord {
                        id: tid,
                        path: path.clone(),
                        title: fields
                            .get("title")
                            .cloned()
                            .unwrap_or_else(|| "Unknown Track".to_string()),
                        artist: fields
                            .get("artist")
                            .cloned()
                            .unwrap_or_else(|| "Unknown Artist".to_string()),
                        album: fields
                            .get("album")
                            .cloned()
                            .unwrap_or_else(|| "Unknown Album".to_string()),
                        album_artist: fields.get("albumArtist").cloned().unwrap_or_default(),
                        genre: fields.get("genre").cloned().unwrap_or_default(),
                        year: fields.get("year").and_then(|s| s.parse().ok()),
                        track_num: fields.get("trackNum").and_then(|s| s.parse().ok()),
                        duration_ms: fields
                            .get("durationMs")
                            .and_then(|s| s.parse().ok())
                            .unwrap_or(0),
                        artwork_path: fields.get("artworkPath").cloned(),
                        accent_color: fields.get("accentColor").cloned(),
                        added_at: fields
                            .get("addedAt")
                            .and_then(|s| s.parse().ok())
                            .unwrap_or(0),
                        file_size: fields
                            .get("fileSize")
                            .and_then(|s| s.parse().ok())
                            .unwrap_or(0),
                        modified_at: fields
                            .get("modifiedAt")
                            .and_then(|s| s.parse().ok())
                            .unwrap_or(0),
                    };
                    db.upsert_track(&record)?;
                    report.tracks_imported += 1;
                }
            }

            // Liked tracks list
            if let Some(liked_raw) = props.get("liked").or_else(|| props.get("likedTracks")) {
                for item in liked_raw.split([',', ';']) {
                    let id_or_path = item.trim();
                    if id_or_path.is_empty() {
                        continue;
                    }
                    if db.get_track_by_id(id_or_path)?.is_some() {
                        db.set_track_liked(id_or_path, true, 1_700_000_000)?;
                        report.liked_imported += 1;
                    } else if let Some(track) = db.get_track_by_path(id_or_path)? {
                        db.set_track_liked(&track.id, true, 1_700_000_000)?;
                        report.liked_imported += 1;
                    }
                }
            }
        }
    }

    // 3. home-v1/*
    let home_dir = legacy_dir.join("home-v1");
    if home_dir.is_dir() {
        if let Ok(entries) = std::fs::read_dir(&home_dir) {
            let mut paths: Vec<PathBuf> = entries.flatten().map(|e| e.path()).collect();
            paths.sort();
            for path in paths {
                if !path.is_file() {
                    continue;
                }
                let file_name = path
                    .file_name()
                    .and_then(|n| n.to_str())
                    .unwrap_or_default()
                    .to_string();
                let Ok(content) = std::fs::read_to_string(&path) else {
                    continue;
                };

                if file_name.ends_with(".json") {
                    if let Ok(playlists) = serde_json::from_str::<Vec<LegacyPlaylist>>(&content) {
                        for pl in playlists {
                            let pl_id = pl
                                .id
                                .unwrap_or_else(|| format!("pl_{}", fnv1a_hex(pl.name.as_bytes())));
                            db.upsert_playlist(
                                &pl_id,
                                &pl.name,
                                pl.description.as_deref().unwrap_or(""),
                                pl.created_at.unwrap_or(1_700_000_000),
                                pl.updated_at.unwrap_or(1_700_000_000),
                            )?;
                            for (pos, tid) in pl.track_ids.iter().enumerate() {
                                if db.get_track_by_id(tid)?.is_some() {
                                    db.add_track_to_playlist(
                                        &pl_id,
                                        tid,
                                        pos as i64,
                                        1_700_000_000,
                                    )?;
                                }
                            }
                            report.playlists_imported += 1;
                        }
                        continue;
                    }
                }

                // Store any other home-v1 config/pinned file as a legacy setting
                db.set_setting(&format!("legacy.home.{file_name}"), content.trim())?;
                report.settings_imported += 1;
            }
        }
    }

    // 4. eq-presets-v1.json
    let eq_file = legacy_dir.join("eq-presets-v1.json");
    if eq_file.exists() {
        if let Ok(content) = std::fs::read_to_string(&eq_file) {
            if let Ok(presets) = serde_json::from_str::<Vec<LegacyEqPreset>>(&content) {
                for p in presets {
                    let id = p
                        .id
                        .unwrap_or_else(|| format!("eq_{}", fnv1a_hex(p.name.as_bytes())));
                    let record = EqPresetRecord {
                        id,
                        name: p.name,
                        preamp_db: p.preamp_db.unwrap_or(0.0),
                        bands_db: p.bands,
                        is_builtin: p.is_builtin.unwrap_or(false),
                    };
                    db.upsert_eq_preset(&record)?;
                    report.eq_presets_imported += 1;
                }
            }
        }
    }

    // 5. listening-stats-v1.json
    let stats_file = legacy_dir.join("listening-stats-v1.json");
    if stats_file.exists() {
        if let Ok(content) = std::fs::read_to_string(&stats_file) {
            if let Ok(stats) = serde_json::from_str::<LegacyListeningStats>(&content) {
                for entry in stats.plays {
                    if db.get_track_by_id(&entry.track_id)?.is_some() {
                        db.record_play(
                            &entry.track_id,
                            entry.played_at,
                            entry.duration_played_ms.unwrap_or(0),
                        )?;
                        report.play_history_imported += 1;
                    }
                }
            }
        }
    }

    db.set_setting(MIGRATION_FLAG_KEY, "true")?;
    Ok(report)
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct LegacyPlaylist {
    id: Option<String>,
    name: String,
    description: Option<String>,
    created_at: Option<i64>,
    updated_at: Option<i64>,
    #[serde(default)]
    track_ids: Vec<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct LegacyEqPreset {
    id: Option<String>,
    name: String,
    preamp_db: Option<f32>,
    #[serde(alias = "bandsDb", default)]
    bands: Vec<f32>,
    is_builtin: Option<bool>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct LegacyListeningStats {
    #[serde(default)]
    plays: Vec<LegacyPlayEntry>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct LegacyPlayEntry {
    track_id: String,
    played_at: i64,
    duration_played_ms: Option<i64>,
}

/// Parses a Java `.properties` file into a key-value map.
pub fn parse_properties(content: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    for raw_line in content.lines() {
        let line = raw_line.trim();
        if line.is_empty() || line.starts_with('#') || line.starts_with('!') {
            continue;
        }
        if let Some((k, v)) = split_unescaped_kv(line) {
            map.insert(unescape_prop(k.trim()), unescape_prop(v.trim()));
        }
    }
    map
}

fn split_unescaped_kv(line: &str) -> Option<(&str, &str)> {
    let mut escaped = false;
    for (idx, ch) in line.char_indices() {
        if escaped {
            escaped = false;
            continue;
        }
        if ch == '\\' {
            escaped = true;
            continue;
        }
        if ch == '=' || ch == ':' {
            return Some((&line[..idx], &line[idx + 1..]));
        }
    }
    None
}

fn unescape_prop(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars();
    while let Some(c) = chars.next() {
        if c == '\\' {
            match chars.next() {
                Some('n') => out.push('\n'),
                Some('r') => out.push('\r'),
                Some('t') => out.push('\t'),
                Some(other) => out.push(other),
                None => break,
            }
        } else {
            out.push(c);
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_properties_handles_escapes_and_comments() {
        let raw = r#"
# Comment line
! Another comment
folders=/Users/test/Music;/Volumes/NAS\:Audio
library.version=0.26.1
"#;
        let props = parse_properties(raw);
        assert_eq!(
            props.get("folders").map(String::as_str),
            Some("/Users/test/Music;/Volumes/NAS:Audio")
        );
        assert_eq!(props.get("library.version").map(String::as_str), Some("0.26.1"));
    }

    #[test]
    fn test_migration_imports_all_legacy_files_and_is_idempotent() {
        let tmp = std::env::temp_dir().join(format!("umimuko_mig_test_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&tmp);
        std::fs::create_dir_all(tmp.join("home-v1")).unwrap();

        std::fs::write(
            tmp.join("folders-v1.properties"),
            "folders=/Music/Rock;/Music/Jazz\n",
        )
        .unwrap();

        std::fs::write(
            tmp.join("library-v1.properties"),
            "track.t1.path=/Music/Rock/a.flac\n\
             track.t1.title=Time\n\
             track.t1.artist=Pink Floyd\n\
             track.t1.album=DSOTM\n\
             track.t1.durationMs=413000\n\
             liked=t1\n\
             theme=dark\n",
        )
        .unwrap();

        std::fs::write(
            tmp.join("home-v1/playlists.json"),
            r#"[{"id":"pl1","name":"Classics","trackIds":["t1"]}]"#,
        )
        .unwrap();
        std::fs::write(tmp.join("home-v1/pinned.json"), r#"["pl1"]"#).unwrap();

        std::fs::write(
            tmp.join("eq-presets-v1.json"),
            r#"[{"name":"Warm","preampDb":-1.5,"bands":[3,2,1,0,0,0,0,-1,-1,-2]}]"#,
        )
        .unwrap();

        std::fs::write(
            tmp.join("listening-stats-v1.json"),
            r#"{"plays":[{"trackId":"t1","playedAt":1700001000,"durationPlayedMs":200000}]}"#,
        )
        .unwrap();

        let db = Database::open_in_memory().unwrap();
        let first = migrate_from_legacy_dir(&db, &tmp).unwrap();
        assert!(!first.already_migrated);
        assert!(first.legacy_dir_found);
        assert_eq!(first.folders_imported, 2);
        assert_eq!(first.tracks_imported, 1);
        assert_eq!(first.liked_imported, 1);
        assert_eq!(first.playlists_imported, 1);
        assert_eq!(first.eq_presets_imported, 1);
        assert_eq!(first.play_history_imported, 1);
        assert!(first.settings_imported >= 2);

        assert_eq!(db.list_folders().unwrap().len(), 2);
        assert_eq!(db.list_tracks().unwrap().len(), 1);
        assert!(db.is_track_liked("t1").unwrap());
        assert_eq!(db.list_playlists().unwrap()[0].track_ids, vec!["t1"]);
        assert_eq!(db.list_eq_presets().unwrap().len(), 1);
        assert_eq!(db.listening_stats(5).unwrap().total_plays, 1);

        // Second run must be a no-op
        let second = migrate_from_legacy_dir(&db, &tmp).unwrap();
        assert!(second.already_migrated);
        assert_eq!(second.tracks_imported, 0);
        assert_eq!(db.list_tracks().unwrap().len(), 1);

        let _ = std::fs::remove_dir_all(&tmp);
    }
}
