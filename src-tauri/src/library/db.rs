use rusqlite::{params, Connection, OptionalExtension};
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum DbError {
    #[error("SQLite error: {0}")]
    Sqlite(#[from] rusqlite::Error),
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("Could not resolve platform app data directory")]
    NoAppDataDir,
}

pub type DbResult<T> = Result<T, DbError>;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct TrackRecord {
    pub id: String,
    pub path: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub genre: String,
    pub year: Option<i32>,
    pub track_num: Option<i32>,
    pub duration_ms: i64,
    pub artwork_path: Option<String>,
    pub accent_color: Option<String>,
    pub added_at: i64,
    pub file_size: i64,
    pub modified_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct FolderRecord {
    pub path: String,
    pub added_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct PlaylistRecord {
    pub id: String,
    pub name: String,
    pub description: String,
    pub created_at: i64,
    pub updated_at: i64,
    pub track_ids: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct PlayHistoryEntry {
    pub id: i64,
    pub track_id: String,
    pub played_at: i64,
    pub duration_played_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct EqPresetRecord {
    pub id: String,
    pub name: String,
    pub preamp_db: f32,
    pub bands_db: Vec<f32>,
    pub is_builtin: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct ListeningStatsSummary {
    pub total_plays: i64,
    pub total_duration_ms: i64,
    pub top_track_ids: Vec<(String, i64)>,
}

/// Returns the platform-specific application data directory for Umimuko Music:
/// - macOS: `~/Library/Application Support/Umimuko Music`
/// - Linux: `$XDG_DATA_HOME/umimuko-music` (or `~/.local/share/umimuko-music`)
/// - Windows: `%LOCALAPPDATA%\UmimukoMusic` (or `%APPDATA%\UmimukoMusic`)
pub fn app_data_dir() -> DbResult<PathBuf> {
    #[cfg(target_os = "macos")]
    {
        let base = dirs::data_dir().ok_or(DbError::NoAppDataDir)?;
        return Ok(base.join("Umimuko Music"));
    }
    #[cfg(target_os = "windows")]
    {
        let base = dirs::data_local_dir()
            .or_else(dirs::data_dir)
            .ok_or(DbError::NoAppDataDir)?;
        return Ok(base.join("UmimukoMusic"));
    }
    #[cfg(all(not(target_os = "macos"), not(target_os = "windows")))]
    {
        let base = dirs::data_dir().ok_or(DbError::NoAppDataDir)?;
        return Ok(base.join("umimuko-music"));
    }
}

pub struct Database {
    conn: Connection,
}

impl Database {
    /// Opens (or creates) the SQLite database in the default platform app data directory.
    pub fn open_default() -> DbResult<Self> {
        let dir = app_data_dir()?;
        std::fs::create_dir_all(&dir)?;
        Self::open_at(&dir.join("library.db"))
    }

    /// Opens (or creates) the SQLite database at a specific file path.
    pub fn open_at(path: &Path) -> DbResult<Self> {
        if let Some(parent) = path.parent() {
            if !parent.as_os_str().is_empty() {
                std::fs::create_dir_all(parent)?;
            }
        }
        let conn = Connection::open(path)?;
        let db = Self { conn };
        db.init_schema()?;
        Ok(db)
    }

    /// Opens an in-memory SQLite database (useful for unit tests).
    pub fn open_in_memory() -> DbResult<Self> {
        let conn = Connection::open_in_memory()?;
        let db = Self { conn };
        db.init_schema()?;
        Ok(db)
    }

    pub fn connection(&self) -> &Connection {
        &self.conn
    }

    fn init_schema(&self) -> DbResult<()> {
        self.conn.execute_batch(
            r#"
            PRAGMA foreign_keys = ON;
            PRAGMA journal_mode = WAL;

            CREATE TABLE IF NOT EXISTS tracks (
                id TEXT PRIMARY KEY,
                path TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                album_artist TEXT NOT NULL,
                genre TEXT NOT NULL,
                year INTEGER,
                track_num INTEGER,
                duration_ms INTEGER NOT NULL,
                artwork_path TEXT,
                accent_color TEXT,
                added_at INTEGER NOT NULL,
                file_size INTEGER NOT NULL,
                modified_at INTEGER NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_tracks_path ON tracks(path);
            CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks(artist);
            CREATE INDEX IF NOT EXISTS idx_tracks_album ON tracks(album);

            CREATE TABLE IF NOT EXISTS folders (
                path TEXT PRIMARY KEY,
                added_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS playlists (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS playlist_tracks (
                playlist_id TEXT NOT NULL,
                track_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                added_at INTEGER NOT NULL,
                PRIMARY KEY (playlist_id, track_id),
                FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
                FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS liked_tracks (
                track_id TEXT PRIMARY KEY,
                liked_at INTEGER NOT NULL,
                FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS play_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                track_id TEXT NOT NULL,
                played_at INTEGER NOT NULL,
                duration_played_ms INTEGER NOT NULL,
                FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
            );

            CREATE INDEX IF NOT EXISTS idx_play_history_track ON play_history(track_id);
            CREATE INDEX IF NOT EXISTS idx_play_history_played_at ON play_history(played_at DESC);

            CREATE TABLE IF NOT EXISTS eq_presets (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL UNIQUE,
                preamp_db REAL NOT NULL DEFAULT 0.0,
                bands_json TEXT NOT NULL,
                is_builtin INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            "#,
        )?;
        Ok(())
    }

    // ---- Tracks ----

    pub fn upsert_track(&self, track: &TrackRecord) -> DbResult<()> {
        self.conn.execute(
            r#"
            INSERT INTO tracks (
                id, path, title, artist, album, album_artist, genre, year, track_num,
                duration_ms, artwork_path, accent_color, added_at, file_size, modified_at
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)
            ON CONFLICT(path) DO UPDATE SET
                title = excluded.title,
                artist = excluded.artist,
                album = excluded.album,
                album_artist = excluded.album_artist,
                genre = excluded.genre,
                year = excluded.year,
                track_num = excluded.track_num,
                duration_ms = excluded.duration_ms,
                artwork_path = COALESCE(excluded.artwork_path, tracks.artwork_path),
                accent_color = COALESCE(excluded.accent_color, tracks.accent_color),
                file_size = excluded.file_size,
                modified_at = excluded.modified_at
            "#,
            params![
                track.id,
                track.path,
                track.title,
                track.artist,
                track.album,
                track.album_artist,
                track.genre,
                track.year,
                track.track_num,
                track.duration_ms,
                track.artwork_path,
                track.accent_color,
                track.added_at,
                track.file_size,
                track.modified_at,
            ],
        )?;
        Ok(())
    }

    pub fn get_track_by_id(&self, id: &str) -> DbResult<Option<TrackRecord>> {
        self.conn
            .query_row(
                "SELECT id, path, title, artist, album, album_artist, genre, year, track_num,
                        duration_ms, artwork_path, accent_color, added_at, file_size, modified_at
                 FROM tracks WHERE id = ?1",
                params![id],
                Self::map_track_row,
            )
            .optional()
            .map_err(Into::into)
    }

    pub fn get_track_by_path(&self, path: &str) -> DbResult<Option<TrackRecord>> {
        self.conn
            .query_row(
                "SELECT id, path, title, artist, album, album_artist, genre, year, track_num,
                        duration_ms, artwork_path, accent_color, added_at, file_size, modified_at
                 FROM tracks WHERE path = ?1",
                params![path],
                Self::map_track_row,
            )
            .optional()
            .map_err(Into::into)
    }

    pub fn list_tracks(&self) -> DbResult<Vec<TrackRecord>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, path, title, artist, album, album_artist, genre, year, track_num,
                    duration_ms, artwork_path, accent_color, added_at, file_size, modified_at
             FROM tracks
             ORDER BY artist COLLATE NOCASE, album COLLATE NOCASE, COALESCE(track_num, 0), title COLLATE NOCASE",
        )?;
        let rows = stmt.query_map([], Self::map_track_row)?;
        rows.collect::<Result<Vec<_>, _>>().map_err(Into::into)
    }

    pub fn delete_track(&self, id: &str) -> DbResult<bool> {
        let affected = self.conn.execute("DELETE FROM tracks WHERE id = ?1", params![id])?;
        Ok(affected > 0)
    }

    fn map_track_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<TrackRecord> {
        Ok(TrackRecord {
            id: row.get(0)?,
            path: row.get(1)?,
            title: row.get(2)?,
            artist: row.get(3)?,
            album: row.get(4)?,
            album_artist: row.get(5)?,
            genre: row.get(6)?,
            year: row.get(7)?,
            track_num: row.get(8)?,
            duration_ms: row.get(9)?,
            artwork_path: row.get(10)?,
            accent_color: row.get(11)?,
            added_at: row.get(12)?,
            file_size: row.get(13)?,
            modified_at: row.get(14)?,
        })
    }

    // ---- Folders ----

    pub fn add_folder(&self, path: &str, added_at: i64) -> DbResult<()> {
        self.conn.execute(
            "INSERT OR IGNORE INTO folders (path, added_at) VALUES (?1, ?2)",
            params![path, added_at],
        )?;
        Ok(())
    }

    pub fn remove_folder(&self, path: &str) -> DbResult<bool> {
        let affected = self.conn.execute("DELETE FROM folders WHERE path = ?1", params![path])?;
        Ok(affected > 0)
    }

    pub fn list_folders(&self) -> DbResult<Vec<FolderRecord>> {
        let mut stmt = self
            .conn
            .prepare("SELECT path, added_at FROM folders ORDER BY added_at ASC")?;
        let rows = stmt.query_map([], |row| {
            Ok(FolderRecord {
                path: row.get(0)?,
                added_at: row.get(1)?,
            })
        })?;
        rows.collect::<Result<Vec<_>, _>>().map_err(Into::into)
    }

    // ---- Playlists ----

    pub fn upsert_playlist(
        &self,
        id: &str,
        name: &str,
        description: &str,
        created_at: i64,
        updated_at: i64,
    ) -> DbResult<()> {
        self.conn.execute(
            r#"
            INSERT INTO playlists (id, name, description, created_at, updated_at)
            VALUES (?1, ?2, ?3, ?4, ?5)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                description = excluded.description,
                updated_at = excluded.updated_at
            "#,
            params![id, name, description, created_at, updated_at],
        )?;
        Ok(())
    }

    pub fn add_track_to_playlist(
        &self,
        playlist_id: &str,
        track_id: &str,
        position: i64,
        added_at: i64,
    ) -> DbResult<()> {
        self.conn.execute(
            r#"
            INSERT INTO playlist_tracks (playlist_id, track_id, position, added_at)
            VALUES (?1, ?2, ?3, ?4)
            ON CONFLICT(playlist_id, track_id) DO UPDATE SET position = excluded.position
            "#,
            params![playlist_id, track_id, position, added_at],
        )?;
        Ok(())
    }

    pub fn remove_track_from_playlist(&self, playlist_id: &str, track_id: &str) -> DbResult<bool> {
        let affected = self.conn.execute(
            "DELETE FROM playlist_tracks WHERE playlist_id = ?1 AND track_id = ?2",
            params![playlist_id, track_id],
        )?;
        Ok(affected > 0)
    }

    pub fn list_playlists(&self) -> DbResult<Vec<PlaylistRecord>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, name, description, created_at, updated_at FROM playlists ORDER BY created_at ASC",
        )?;
        let rows = stmt.query_map([], |row| {
            Ok((
                row.get::<_, String>(0)?,
                row.get::<_, String>(1)?,
                row.get::<_, String>(2)?,
                row.get::<_, i64>(3)?,
                row.get::<_, i64>(4)?,
            ))
        })?;
        let mut playlists = Vec::new();
        for row in rows {
            let (id, name, description, created_at, updated_at) = row?;
            let mut t_stmt = self.conn.prepare(
                "SELECT track_id FROM playlist_tracks WHERE playlist_id = ?1 ORDER BY position ASC",
            )?;
            let track_ids = t_stmt
                .query_map(params![&id], |r| r.get::<_, String>(0))?
                .collect::<Result<Vec<_>, _>>()?;
            playlists.push(PlaylistRecord {
                id,
                name,
                description,
                created_at,
                updated_at,
                track_ids,
            });
        }
        Ok(playlists)
    }

    pub fn delete_playlist(&self, id: &str) -> DbResult<bool> {
        let affected = self.conn.execute("DELETE FROM playlists WHERE id = ?1", params![id])?;
        Ok(affected > 0)
    }

    // ---- Liked Tracks ----

    pub fn set_track_liked(&self, track_id: &str, liked: bool, liked_at: i64) -> DbResult<()> {
        if liked {
            self.conn.execute(
                "INSERT OR IGNORE INTO liked_tracks (track_id, liked_at) VALUES (?1, ?2)",
                params![track_id, liked_at],
            )?;
        } else {
            self.conn.execute(
                "DELETE FROM liked_tracks WHERE track_id = ?1",
                params![track_id],
            )?;
        }
        Ok(())
    }

    pub fn is_track_liked(&self, track_id: &str) -> DbResult<bool> {
        let found: Option<i64> = self
            .conn
            .query_row(
                "SELECT liked_at FROM liked_tracks WHERE track_id = ?1",
                params![track_id],
                |r| r.get(0),
            )
            .optional()?;
        Ok(found.is_some())
    }

    pub fn list_liked_track_ids(&self) -> DbResult<Vec<String>> {
        let mut stmt = self
            .conn
            .prepare("SELECT track_id FROM liked_tracks ORDER BY liked_at DESC")?;
        let rows = stmt.query_map([], |r| r.get(0))?;
        rows.collect::<Result<Vec<_>, _>>().map_err(Into::into)
    }

    // ---- Play History ----

    pub fn record_play(&self, track_id: &str, played_at: i64, duration_played_ms: i64) -> DbResult<i64> {
        self.conn.execute(
            "INSERT INTO play_history (track_id, played_at, duration_played_ms) VALUES (?1, ?2, ?3)",
            params![track_id, played_at, duration_played_ms],
        )?;
        Ok(self.conn.last_insert_rowid())
    }

    pub fn list_recent_plays(&self, limit: usize) -> DbResult<Vec<PlayHistoryEntry>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, track_id, played_at, duration_played_ms
             FROM play_history ORDER BY played_at DESC LIMIT ?1",
        )?;
        let rows = stmt.query_map(params![limit as i64], |r| {
            Ok(PlayHistoryEntry {
                id: r.get(0)?,
                track_id: r.get(1)?,
                played_at: r.get(2)?,
                duration_played_ms: r.get(3)?,
            })
        })?;
        rows.collect::<Result<Vec<_>, _>>().map_err(Into::into)
    }

    pub fn listening_stats(&self, top_limit: usize) -> DbResult<ListeningStatsSummary> {
        let (total_plays, total_duration_ms): (i64, i64) = self.conn.query_row(
            "SELECT COUNT(*), COALESCE(SUM(duration_played_ms), 0) FROM play_history",
            [],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )?;
        let mut stmt = self.conn.prepare(
            "SELECT track_id, COUNT(*) as cnt
             FROM play_history
             GROUP BY track_id
             ORDER BY cnt DESC
             LIMIT ?1",
        )?;
        let top_track_ids = stmt
            .query_map(params![top_limit as i64], |r| Ok((r.get(0)?, r.get(1)?)))?
            .collect::<Result<Vec<(String, i64)>, _>>()?;
        Ok(ListeningStatsSummary {
            total_plays,
            total_duration_ms,
            top_track_ids,
        })
    }

    // ---- EQ Presets ----

    pub fn upsert_eq_preset(&self, preset: &EqPresetRecord) -> DbResult<()> {
        let bands_json = serde_json::to_string(&preset.bands_db).unwrap_or_else(|_| "[]".into());
        self.conn.execute(
            r#"
            INSERT INTO eq_presets (id, name, preamp_db, bands_json, is_builtin)
            VALUES (?1, ?2, ?3, ?4, ?5)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                preamp_db = excluded.preamp_db,
                bands_json = excluded.bands_json,
                is_builtin = excluded.is_builtin
            "#,
            params![
                preset.id,
                preset.name,
                preset.preamp_db,
                bands_json,
                preset.is_builtin as i32,
            ],
        )?;
        Ok(())
    }

    pub fn list_eq_presets(&self) -> DbResult<Vec<EqPresetRecord>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, name, preamp_db, bands_json, is_builtin FROM eq_presets ORDER BY is_builtin DESC, name ASC",
        )?;
        let rows = stmt.query_map([], |r| {
            let bands_json: String = r.get(3)?;
            let bands_db: Vec<f32> = serde_json::from_str(&bands_json).unwrap_or_default();
            Ok(EqPresetRecord {
                id: r.get(0)?,
                name: r.get(1)?,
                preamp_db: r.get(2)?,
                bands_db,
                is_builtin: r.get::<_, i32>(4)? != 0,
            })
        })?;
        rows.collect::<Result<Vec<_>, _>>().map_err(Into::into)
    }

    pub fn delete_eq_preset(&self, id: &str) -> DbResult<bool> {
        let affected = self
            .conn
            .execute("DELETE FROM eq_presets WHERE id = ?1", params![id])?;
        Ok(affected > 0)
    }

    // ---- Settings ----

    pub fn set_setting(&self, key: &str, value: &str) -> DbResult<()> {
        self.conn.execute(
            "INSERT INTO settings (key, value) VALUES (?1, ?2)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            params![key, value],
        )?;
        Ok(())
    }

    pub fn get_setting(&self, key: &str) -> DbResult<Option<String>> {
        self.conn
            .query_row(
                "SELECT value FROM settings WHERE key = ?1",
                params![key],
                |r| r.get(0),
            )
            .optional()
            .map_err(Into::into)
    }

    pub fn list_settings(&self) -> DbResult<Vec<(String, String)>> {
        let mut stmt = self
            .conn
            .prepare("SELECT key, value FROM settings ORDER BY key ASC")?;
        let rows = stmt.query_map([], |r| Ok((r.get(0)?, r.get(1)?)))?;
        rows.collect::<Result<Vec<_>, _>>().map_err(Into::into)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_track(id: &str, path: &str) -> TrackRecord {
        TrackRecord {
            id: id.to_string(),
            path: path.to_string(),
            title: "Midnight City".to_string(),
            artist: "M83".to_string(),
            album: "Hurry Up, We're Dreaming".to_string(),
            album_artist: "M83".to_string(),
            genre: "Synthwave".to_string(),
            year: Some(2011),
            track_num: Some(2),
            duration_ms: 243_000,
            artwork_path: Some("/cache/covers-v1/abc.png".to_string()),
            accent_color: Some("#4F46E5".to_string()),
            added_at: 1_700_000_000,
            file_size: 9_800_000,
            modified_at: 1_700_000_000,
        }
    }

    #[test]
    fn test_app_data_dir_resolves_platform_name() {
        let dir = app_data_dir().expect("app_data_dir should resolve");
        let name = dir.file_name().and_then(|n| n.to_str()).unwrap_or("");
        #[cfg(target_os = "macos")]
        assert_eq!(name, "Umimuko Music");
        #[cfg(target_os = "windows")]
        assert_eq!(name, "UmimukoMusic");
        #[cfg(all(not(target_os = "macos"), not(target_os = "windows")))]
        assert_eq!(name, "umimuko-music");
    }

    #[test]
    fn test_track_crud_and_upsert() {
        let db = Database::open_in_memory().unwrap();
        let mut track = sample_track("t1", "/music/m83.flac");
        db.upsert_track(&track).unwrap();

        let fetched = db.get_track_by_id("t1").unwrap().unwrap();
        assert_eq!(fetched, track);

        // Update metadata on same path
        track.title = "Midnight City (Remastered)".to_string();
        track.artwork_path = None; // Should preserve existing artwork_path via COALESCE
        db.upsert_track(&track).unwrap();

        let updated = db.get_track_by_path("/music/m83.flac").unwrap().unwrap();
        assert_eq!(updated.title, "Midnight City (Remastered)");
        assert_eq!(
            updated.artwork_path.as_deref(),
            Some("/cache/covers-v1/abc.png")
        );

        assert_eq!(db.list_tracks().unwrap().len(), 1);
        assert!(db.delete_track("t1").unwrap());
        assert!(db.list_tracks().unwrap().is_empty());
    }

    #[test]
    fn test_folders_playlists_likes_and_history() {
        let db = Database::open_in_memory().unwrap();
        db.upsert_track(&sample_track("t1", "/music/1.mp3")).unwrap();
        db.upsert_track(&sample_track("t2", "/music/2.mp3")).unwrap();

        db.add_folder("/music", 100).unwrap();
        db.add_folder("/music", 200).unwrap(); // idempotent
        assert_eq!(db.list_folders().unwrap().len(), 1);

        db.upsert_playlist("p1", "Favorites", "Top tracks", 100, 100)
            .unwrap();
        db.add_track_to_playlist("p1", "t1", 0, 101).unwrap();
        db.add_track_to_playlist("p1", "t2", 1, 102).unwrap();

        let playlists = db.list_playlists().unwrap();
        assert_eq!(playlists.len(), 1);
        assert_eq!(playlists[0].track_ids, vec!["t1", "t2"]);

        db.set_track_liked("t1", true, 150).unwrap();
        assert!(db.is_track_liked("t1").unwrap());
        assert_eq!(db.list_liked_track_ids().unwrap(), vec!["t1"]);
        db.set_track_liked("t1", false, 160).unwrap();
        assert!(!db.is_track_liked("t1").unwrap());

        db.record_play("t1", 1000, 120_000).unwrap();
        db.record_play("t1", 2000, 240_000).unwrap();
        db.record_play("t2", 3000, 60_000).unwrap();

        let recent = db.list_recent_plays(2).unwrap();
        assert_eq!(recent.len(), 2);
        assert_eq!(recent[0].track_id, "t2");

        let stats = db.listening_stats(5).unwrap();
        assert_eq!(stats.total_plays, 3);
        assert_eq!(stats.total_duration_ms, 420_000);
        assert_eq!(stats.top_track_ids[0], ("t1".to_string(), 2));
    }

    #[test]
    fn test_eq_presets_and_settings() {
        let db = Database::open_in_memory().unwrap();
        let preset = EqPresetRecord {
            id: "bass".to_string(),
            name: "Bass Boost".to_string(),
            preamp_db: -2.0,
            bands_db: vec![5.0, 4.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
            is_builtin: false,
        };
        db.upsert_eq_preset(&preset).unwrap();
        let presets = db.list_eq_presets().unwrap();
        assert_eq!(presets.len(), 1);
        assert_eq!(presets[0].bands_db.len(), 10);

        db.set_setting("theme", "dark").unwrap();
        db.set_setting("theme", "light").unwrap();
        assert_eq!(db.get_setting("theme").unwrap().as_deref(), Some("light"));
    }
}
