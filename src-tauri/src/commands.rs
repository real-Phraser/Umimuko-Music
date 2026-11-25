use crate::audio::dsp::{CrossfadeCurve, EqConfig, EQ_BAND_COUNT};
use crate::audio::engine::{
    AudioEngine, EngineSnapshot, PlaybackStatus, MAX_PLAYBACK_RATE, MIN_PLAYBACK_RATE,
};
use crate::audio::queue::{RepeatMode, TrackRef};
use crate::library::db::{Database, EqPresetRecord, PlaylistRecord, TrackRecord};
use crate::library::duplicates::{DuplicateDetector, DuplicateMatchKind};
use crate::library::metadata::{write_metadata, MetadataUpdate};
use crate::library::scanner::scan_folders as run_folder_scan;
use crate::lyrics::parser::{LyricsKind, SpicyLyrics as ParsedLyrics};
use crate::lyrics::service::{LyricsQuery, SpicyLyricsService};
use crate::os::media_session::MediaSession;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::{Instant, SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Emitter, Manager, State};

const SETTINGS_KEY_APP: &str = "app_settings";
const SETTINGS_KEY_PINNED: &str = "pinned_track_ids";
const SETTINGS_KEY_PINNED_PLAYLISTS: &str = "pinned_playlist_ids";

// ---------------------------------------------------------------------------
// Frontend IPC DTOs (matching src/types/ipc.ts 1:1)
// ---------------------------------------------------------------------------

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum RepeatModeDto {
    Off,
    All,
    One,
}

impl From<RepeatMode> for RepeatModeDto {
    fn from(value: RepeatMode) -> Self {
        match value {
            RepeatMode::Off => Self::Off,
            RepeatMode::All => Self::All,
            RepeatMode::One => Self::One,
        }
    }
}

impl From<RepeatModeDto> for RepeatMode {
    fn from(value: RepeatModeDto) -> Self {
        match value {
            RepeatModeDto::Off => Self::Off,
            RepeatModeDto::All => Self::All,
            RepeatModeDto::One => Self::One,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TrackDto {
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
    pub liked: bool,
    pub play_count: i64,
    pub last_played_at: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaybackStateDto {
    pub status: PlaybackStatus,
    pub current_track_id: Option<String>,
    pub current_track: Option<TrackDto>,
    pub position_ms: u64,
    pub duration_ms: u64,
    pub volume: f64,
    pub muted: bool,
    pub shuffle: bool,
    pub repeat: RepeatModeDto,
    pub sleep_timer_remaining_ms: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QueueSnapshotDto {
    pub items: Vec<TrackDto>,
    pub current_index: isize,
    pub shuffle: bool,
    pub repeat: RepeatModeDto,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaybackTickPayload {
    pub position_ms: u64,
    pub duration_ms: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryScanProgressDto {
    pub scanning: bool,
    pub scanned: usize,
    pub total: usize,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub current_file: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EqualizerConfigDto {
    pub enabled: bool,
    pub preamp_db: f32,
    pub bands: Vec<f32>,
    pub preset_name: Option<String>,
}

impl Default for EqualizerConfigDto {
    fn default() -> Self {
        Self {
            enabled: false,
            preamp_db: 0.0,
            bands: vec![0.0; EQ_BAND_COUNT],
            preset_name: Some("Flat".to_string()),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EqualizerPresetDto {
    pub id: String,
    pub name: String,
    pub preamp_db: f32,
    pub bands: Vec<f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub built_in: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveEqPresetInput {
    pub name: String,
    pub preamp_db: f32,
    pub bands: Vec<f32>,
    pub built_in: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CrossfadeConfigDto {
    pub enabled: bool,
    pub duration_ms: u64,
    pub gapless: bool,
}

impl Default for CrossfadeConfigDto {
    fn default() -> Self {
        Self {
            enabled: false,
            duration_ms: 3000,
            gapless: true,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct KeybindingsConfigDto {
    pub play_pause: String,
    pub next_track: String,
    pub prev_track: String,
    pub volume_up: String,
    pub volume_down: String,
    pub toggle_mute: String,
    pub toggle_lyrics: String,
    pub toggle_queue: String,
    pub focus_search: String,
}

impl Default for KeybindingsConfigDto {
    fn default() -> Self {
        Self {
            play_pause: "Space".to_string(),
            next_track: "ArrowRight".to_string(),
            prev_track: "ArrowLeft".to_string(),
            volume_up: "ArrowUp".to_string(),
            volume_down: "ArrowDown".to_string(),
            toggle_mute: "KeyM".to_string(),
            toggle_lyrics: "KeyL".to_string(),
            toggle_queue: "KeyQ".to_string(),
            focus_search: "KeyF".to_string(),
        }
    }
}

fn default_startup_intro() -> bool {
    true
}

fn default_playback_rate() -> f32 {
    1.0
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AppSettingsDto {
    pub theme: String,
    pub adaptive_accent: bool,
    pub folders: Vec<String>,
    #[serde(alias = "spicy_lyrics_api_key")]
    pub spicy_lyrics_api_key: String,
    pub crossfade: CrossfadeConfigDto,
    pub equalizer: EqualizerConfigDto,
    pub keybindings: KeybindingsConfigDto,
    #[serde(default)]
    pub minimize_to_tray: bool,
    #[serde(default)]
    pub close_to_tray: bool,
    #[serde(default = "default_startup_intro")]
    pub startup_intro: bool,
    #[serde(default = "default_playback_rate")]
    pub playback_rate: f32,
    #[serde(default)]
    pub show_mini_player_on_close: bool,
}

impl Default for AppSettingsDto {
    fn default() -> Self {
        Self {
            theme: "dark".to_string(),
            adaptive_accent: true,
            folders: Vec::new(),
            spicy_lyrics_api_key: String::new(),
            crossfade: CrossfadeConfigDto::default(),
            equalizer: EqualizerConfigDto::default(),
            keybindings: KeybindingsConfigDto::default(),
            minimize_to_tray: false,
            close_to_tray: false,
            startup_intro: true,
            playback_rate: 1.0,
            show_mini_player_on_close: false,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaylistDto {
    pub id: String,
    pub name: String,
    pub track_ids: Vec<String>,
    pub created_at: i64,
    pub updated_at: i64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub pinned: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TagUpdateInput {
    pub track_id: String,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub genre: String,
    pub year: Option<i32>,
    pub track_num: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LyricWordDto {
    pub text: String,
    pub start_ms: i64,
    pub end_ms: i64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub is_background: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LyricLineDto {
    pub start_ms: i64,
    pub end_ms: i64,
    pub text: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub words: Option<Vec<LyricWordDto>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub romanized: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub is_instrumental: Option<bool>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SpicyLyricsDto {
    pub track_id: String,
    #[serde(rename = "type")]
    pub lyrics_type: String,
    pub source: String,
    pub lines: Vec<LyricLineDto>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DuplicateGroupDto {
    pub id: String,
    pub reason: String,
    pub tracks: Vec<TrackDto>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TopTrackStatDto {
    pub track: TrackDto,
    pub play_count: i64,
    pub total_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TopArtistStatDto {
    pub artist: String,
    pub play_count: i64,
    pub total_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TopAlbumStatDto {
    pub album: String,
    pub artist: String,
    pub artwork_path: Option<String>,
    pub accent_color: Option<String>,
    pub play_count: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RecentPlayDto {
    pub track: TrackDto,
    pub played_at: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ListeningStatsDto {
    pub total_play_time_ms: i64,
    pub total_plays: i64,
    pub tracks_count: usize,
    pub top_tracks: Vec<TopTrackStatDto>,
    pub top_artists: Vec<TopArtistStatDto>,
    pub top_albums: Vec<TopAlbumStatDto>,
    pub recent_plays: Vec<RecentPlayDto>,
}

// ---------------------------------------------------------------------------
// Shared Application State
// ---------------------------------------------------------------------------

pub struct QueueCache {
    pub tracks: Vec<TrackRef>,
    pub current_index: Option<usize>,
    pub repeat_mode: RepeatMode,
    pub shuffled: bool,
}

impl Default for QueueCache {
    fn default() -> Self {
        Self {
            tracks: Vec::new(),
            current_index: None,
            repeat_mode: RepeatMode::Off,
            shuffled: false,
        }
    }
}

pub struct AppState {
    pub db: Arc<Mutex<Database>>,
    pub engine: Option<Arc<AudioEngine>>,
    pub media_session: Arc<Mutex<Option<MediaSession>>>,
    pub lyrics_service: SpicyLyricsService,
    pub cache_root: PathBuf,
    pub sleep_timer_deadline: Arc<Mutex<Option<Instant>>>,
    pub queue_cache: Arc<Mutex<QueueCache>>,
}

impl AppState {
    pub fn sleep_timer_remaining_ms(&self) -> Option<u64> {
        sleep_remaining_ms(&self.sleep_timer_deadline)
    }

    pub fn current_playback_state(&self) -> PlaybackStateDto {
        let sleep_ms = self.sleep_timer_remaining_ms();
        let db = self.db.lock().unwrap_or_else(|e| e.into_inner());
        if let Some(ref engine) = self.engine {
            let snap = engine.snapshot();
            snapshot_to_playback_dto(&db, &snap, sleep_ms)
        } else {
            PlaybackStateDto {
                status: PlaybackStatus::Stopped,
                current_track_id: None,
                current_track: None,
                position_ms: 0,
                duration_ms: 0,
                volume: 0.8,
                muted: false,
                shuffle: false,
                repeat: RepeatModeDto::Off,
                sleep_timer_remaining_ms: sleep_ms,
            }
        }
    }

    pub fn current_queue_snapshot(&self) -> QueueSnapshotDto {
        let qc = self.queue_cache.lock().unwrap_or_else(|e| e.into_inner());
        let db = self.db.lock().unwrap_or_else(|e| e.into_inner());
        build_queue_dto(&db, &qc.tracks, qc.current_index, qc.shuffled, qc.repeat_mode)
    }
}

// ---------------------------------------------------------------------------
// Helper Functions
// ---------------------------------------------------------------------------

pub fn sleep_remaining_ms(deadline: &Arc<Mutex<Option<Instant>>>) -> Option<u64> {
    let guard = deadline.lock().ok()?;
    let dl = (*guard)?;
    let now = Instant::now();
    if dl > now {
        Some(dl.duration_since(now).as_millis() as u64)
    } else {
        Some(0)
    }
}

fn unix_now_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

fn unix_now_secs() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

pub fn track_record_to_ref(t: &TrackRecord) -> TrackRef {
    TrackRef {
        id: t.id.clone(),
        path: t.path.clone(),
        title: t.title.clone(),
        artist: t.artist.clone(),
        album: t.album.clone(),
        duration_ms: t.duration_ms.max(0) as u64,
        artwork_path: t.artwork_path.clone(),
    }
}

fn track_ref_to_fallback_dto(t: &TrackRef) -> TrackDto {
    TrackDto {
        id: t.id.clone(),
        path: t.path.clone(),
        title: t.title.clone(),
        artist: t.artist.clone(),
        album: t.album.clone(),
        album_artist: t.artist.clone(),
        genre: String::new(),
        year: None,
        track_num: None,
        duration_ms: t.duration_ms as i64,
        artwork_path: t.artwork_path.clone(),
        accent_color: None,
        added_at: 0,
        file_size: 0,
        modified_at: 0,
        liked: false,
        play_count: 0,
        last_played_at: None,
    }
}

fn load_play_stats_map(db: &Database) -> HashMap<String, (i64, Option<i64>)> {
    let mut map = HashMap::new();
    if let Ok(mut stmt) = db.connection().prepare(
        "SELECT track_id, COUNT(*), MAX(played_at) FROM play_history GROUP BY track_id",
    ) {
        if let Ok(rows) = stmt.query_map([], |r| {
            Ok((
                r.get::<_, String>(0)?,
                r.get::<_, i64>(1)?,
                r.get::<_, Option<i64>>(2)?,
            ))
        }) {
            for row in rows.flatten() {
                map.insert(row.0, (row.1, row.2));
            }
        }
    }
    map
}

pub fn hydrate_tracks(db: &Database, records: &[TrackRecord]) -> Vec<TrackDto> {
    let liked_set: HashSet<String> = db
        .list_liked_track_ids()
        .unwrap_or_default()
        .into_iter()
        .collect();
    let stats = load_play_stats_map(db);
    records
        .iter()
        .map(|r| {
            let (play_count, last_played_at) = stats.get(&r.id).copied().unwrap_or((0, None));
            TrackDto {
                id: r.id.clone(),
                path: r.path.clone(),
                title: r.title.clone(),
                artist: r.artist.clone(),
                album: r.album.clone(),
                album_artist: r.album_artist.clone(),
                genre: r.genre.clone(),
                year: r.year,
                track_num: r.track_num,
                duration_ms: r.duration_ms,
                artwork_path: r.artwork_path.clone(),
                accent_color: r.accent_color.clone(),
                added_at: r.added_at,
                file_size: r.file_size,
                modified_at: r.modified_at,
                liked: liked_set.contains(&r.id),
                play_count,
                last_played_at,
            }
        })
        .collect()
}

pub fn hydrate_track(db: &Database, record: &TrackRecord) -> TrackDto {
    let liked = db.is_track_liked(&record.id).unwrap_or(false);
    let (play_count, last_played_at) = db
        .connection()
        .query_row(
            "SELECT COUNT(*), MAX(played_at) FROM play_history WHERE track_id = ?1",
            rusqlite::params![record.id],
            |r| Ok((r.get::<_, i64>(0)?, r.get::<_, Option<i64>>(1)?)),
        )
        .unwrap_or((0, None));
    TrackDto {
        id: record.id.clone(),
        path: record.path.clone(),
        title: record.title.clone(),
        artist: record.artist.clone(),
        album: record.album.clone(),
        album_artist: record.album_artist.clone(),
        genre: record.genre.clone(),
        year: record.year,
        track_num: record.track_num,
        duration_ms: record.duration_ms,
        artwork_path: record.artwork_path.clone(),
        accent_color: record.accent_color.clone(),
        added_at: record.added_at,
        file_size: record.file_size,
        modified_at: record.modified_at,
        liked,
        play_count,
        last_played_at,
    }
}

pub fn build_queue_dto(
    db: &Database,
    tracks: &[TrackRef],
    current_index: Option<usize>,
    shuffled: bool,
    repeat_mode: RepeatMode,
) -> QueueSnapshotDto {
    let all_records = db.list_tracks().unwrap_or_default();
    let hydrated = hydrate_tracks(db, &all_records);
    let by_id: HashMap<String, TrackDto> = hydrated.into_iter().map(|t| (t.id.clone(), t)).collect();

    let items = tracks
        .iter()
        .map(|tr| {
            by_id
                .get(&tr.id)
                .cloned()
                .unwrap_or_else(|| track_ref_to_fallback_dto(tr))
        })
        .collect();

    QueueSnapshotDto {
        items,
        current_index: current_index.map(|i| i as isize).unwrap_or(-1),
        shuffle: shuffled,
        repeat: repeat_mode.into(),
    }
}

pub fn snapshot_to_playback_dto(
    db: &Database,
    snap: &EngineSnapshot,
    sleep_timer_remaining_ms: Option<u64>,
) -> PlaybackStateDto {
    let current_track = snap.current_track.as_ref().map(|tr| {
        db.get_track_by_id(&tr.id)
            .ok()
            .flatten()
            .map(|rec| hydrate_track(db, &rec))
            .unwrap_or_else(|| track_ref_to_fallback_dto(tr))
    });
    PlaybackStateDto {
        status: snap.status,
        current_track_id: snap.current_track.as_ref().map(|t| t.id.clone()),
        current_track,
        position_ms: snap.position_ms,
        duration_ms: snap.duration_ms,
        volume: (snap.volume as f64 / 100.0).clamp(0.0, 1.0),
        muted: snap.muted,
        shuffle: snap.shuffled,
        repeat: snap.repeat_mode.into(),
        sleep_timer_remaining_ms,
    }
}

pub fn state_event_to_playback_dto(
    db: &Database,
    status: PlaybackStatus,
    track: Option<&TrackRef>,
    position_ms: u64,
    duration_ms: u64,
    volume: u8,
    muted: bool,
    shuffle: bool,
    repeat: RepeatMode,
    sleep_timer_remaining_ms: Option<u64>,
) -> PlaybackStateDto {
    let current_track = track.map(|tr| {
        db.get_track_by_id(&tr.id)
            .ok()
            .flatten()
            .map(|rec| hydrate_track(db, &rec))
            .unwrap_or_else(|| track_ref_to_fallback_dto(tr))
    });
    PlaybackStateDto {
        status,
        current_track_id: track.map(|t| t.id.clone()),
        current_track,
        position_ms,
        duration_ms,
        volume: (volume as f64 / 100.0).clamp(0.0, 1.0),
        muted,
        shuffle,
        repeat: repeat.into(),
        sleep_timer_remaining_ms,
    }
}

pub fn load_app_settings(db: &Database) -> AppSettingsDto {
    let mut settings = db
        .get_setting(SETTINGS_KEY_APP)
        .ok()
        .flatten()
        .and_then(|json| serde_json::from_str::<AppSettingsDto>(&json).ok())
        .unwrap_or_default();

    if settings.spicy_lyrics_api_key.trim().is_empty() {
        for key in [
            "spicyLyricsApiKey",
            "spicy_lyrics_api_key",
            "legacy.spicyLyricsApiKey",
            "legacy.spicy_lyrics_api_key",
            "legacy.apiKey",
        ] {
            if let Ok(Some(val)) = db.get_setting(key) {
                let trimmed = val.trim();
                if !trimmed.is_empty() {
                    settings.spicy_lyrics_api_key = trimmed.to_string();
                    break;
                }
            }
        }
    }

    if let Ok(folders) = db.list_folders() {
        let db_folders: Vec<String> = folders.into_iter().map(|f| f.path).collect();
        if !db_folders.is_empty() {
            settings.folders = db_folders;
        }
    }
    settings
}

fn save_app_settings_to_db(db: &Database, settings: &AppSettingsDto) -> Result<(), String> {
    let json = serde_json::to_string(settings).map_err(|e| e.to_string())?;
    db.set_setting(SETTINGS_KEY_APP, &json)
        .map_err(|e| e.to_string())?;

    let existing_folders: HashSet<String> = db
        .list_folders()
        .map_err(|e| e.to_string())?
        .into_iter()
        .map(|f| f.path)
        .collect();
    let desired: HashSet<String> = settings.folders.iter().cloned().collect();
    let now = unix_now_secs();

    for f in &desired {
        if !existing_folders.contains(f) {
            let _ = db.add_folder(f, now);
        }
    }
    for f in &existing_folders {
        if !desired.contains(f) {
            let _ = db.remove_folder(f);
        }
    }
    Ok(())
}

pub fn apply_eq_to_engine(engine: &AudioEngine, config: &EqualizerConfigDto) {
    let mut bands = [0.0_f32; EQ_BAND_COUNT];
    for (i, val) in config.bands.iter().take(EQ_BAND_COUNT).enumerate() {
        bands[i] = *val;
    }
    engine.set_eq_config(EqConfig {
        enabled: config.enabled,
        preamp_db: config.preamp_db,
        band_gains: bands,
    });
}

pub fn apply_crossfade_to_engine(engine: &AudioEngine, config: &CrossfadeConfigDto) {
    let secs = if config.enabled {
        (config.duration_ms as f32) / 1000.0
    } else {
        0.0
    };
    engine.set_crossfade(secs, CrossfadeCurve::EqualPower);
}

pub fn apply_playback_rate_to_engine(engine: &AudioEngine, rate: f32) {
    let clamped = if rate.is_finite() {
        rate.clamp(MIN_PLAYBACK_RATE, MAX_PLAYBACK_RATE)
    } else {
        1.0
    };
    engine.set_playback_rate(clamped);
}

fn playlist_record_to_dto(rec: PlaylistRecord, pinned_ids: &HashSet<String>) -> PlaylistDto {
    let pinned = if pinned_ids.contains(&rec.id) {
        Some(true)
    } else {
        None
    };
    PlaylistDto {
        id: rec.id,
        name: rec.name,
        track_ids: rec.track_ids,
        created_at: rec.created_at,
        updated_at: rec.updated_at,
        pinned,
    }
}

fn convert_parsed_lyrics(track_id: String, parsed: Option<ParsedLyrics>) -> SpicyLyricsDto {
    let Some(lyrics) = parsed else {
        return SpicyLyricsDto {
            track_id,
            lyrics_type: "none".to_string(),
            source: "None".to_string(),
            lines: Vec::new(),
        };
    };

    let lyrics_type = match lyrics.kind {
        LyricsKind::Syllable => "syllable",
        LyricsKind::Line => "line",
        LyricsKind::Static => "plain",
    }
    .to_string();

    let lines = lyrics
        .lines
        .into_iter()
        .map(|l| {
            let start_ms = l.start_ms.unwrap_or(0);
            let end_ms = l.end_ms.unwrap_or(start_ms + 3000);
            let mut all_words: Vec<LyricWordDto> = l
                .words
                .into_iter()
                .map(|w| LyricWordDto {
                    text: w.text,
                    start_ms: w.start_ms,
                    end_ms: w.end_ms,
                    is_background: if w.is_background { Some(true) } else { None },
                })
                .collect();
            for bw in l.background_words {
                all_words.push(LyricWordDto {
                    text: bw.text,
                    start_ms: bw.start_ms,
                    end_ms: bw.end_ms,
                    is_background: Some(true),
                });
            }
            let words = if all_words.is_empty() {
                None
            } else {
                Some(all_words)
            };
            let is_instrumental = if l.text.trim() == "•••" || l.text.trim().is_empty() {
                Some(true)
            } else {
                None
            };
            LyricLineDto {
                start_ms,
                end_ms,
                text: l.text,
                words,
                romanized: l.romanized,
                is_instrumental,
            }
        })
        .collect();

    SpicyLyricsDto {
        track_id,
        lyrics_type,
        source: lyrics.source,
        lines,
    }
}

fn built_in_eq_presets() -> Vec<EqualizerPresetDto> {
    vec![
        EqualizerPresetDto {
            id: "flat".into(),
            name: "Flat".into(),
            preamp_db: 0.0,
            bands: vec![0.0; 10],
            built_in: Some(true),
        },
        EqualizerPresetDto {
            id: "bass-boost".into(),
            name: "Bass Boost".into(),
            preamp_db: -1.5,
            bands: vec![6.0, 5.0, 4.0, 2.0, 0.0, 0.0, 0.0, 1.0, 2.0, 2.0],
            built_in: Some(true),
        },
        EqualizerPresetDto {
            id: "vocal".into(),
            name: "Vocal Clarity".into(),
            preamp_db: 0.0,
            bands: vec![-2.0, -1.0, 0.0, 2.0, 4.0, 5.0, 4.0, 2.0, 0.0, -1.0],
            built_in: Some(true),
        },
        EqualizerPresetDto {
            id: "electronic".into(),
            name: "Electronic".into(),
            preamp_db: -1.0,
            bands: vec![4.5, 3.5, 1.5, 0.0, -1.5, 1.0, 2.5, 4.0, 4.5, 5.0],
            built_in: Some(true),
        },
        EqualizerPresetDto {
            id: "acoustic".into(),
            name: "Acoustic".into(),
            preamp_db: 0.0,
            bands: vec![2.0, 2.0, 1.0, 0.0, 1.0, 2.0, 3.0, 2.5, 2.0, 1.5],
            built_in: Some(true),
        },
        EqualizerPresetDto {
            id: "rock".into(),
            name: "Rock".into(),
            preamp_db: -1.0,
            bands: vec![4.0, 3.0, 1.5, -1.0, -1.5, 0.5, 2.5, 4.0, 4.5, 4.0],
            built_in: Some(true),
        },
        EqualizerPresetDto {
            id: "late-night".into(),
            name: "Late Night".into(),
            preamp_db: 1.0,
            bands: vec![-3.0, -2.0, 0.0, 1.5, 2.0, 2.0, 1.5, 0.0, -2.0, -3.0],
            built_in: Some(true),
        },
    ]
}

// ---------------------------------------------------------------------------
// Tauri Commands (39 commands matching src/api/tauri.ts)
// ---------------------------------------------------------------------------

#[tauri::command]
pub fn get_tracks(state: State<'_, AppState>) -> Result<Vec<TrackDto>, String> {
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let records = db.list_tracks().map_err(|e| e.to_string())?;
    Ok(hydrate_tracks(&db, &records))
}

#[tauri::command]
pub fn scan_folders(
    app: AppHandle,
    state: State<'_, AppState>,
    folders: Option<Vec<String>>,
) -> Result<Vec<TrackDto>, String> {
    let folder_paths: Vec<PathBuf> = {
        let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
        let list = match folders {
            Some(f) if !f.is_empty() => f,
            _ => db
                .list_folders()
                .map_err(|e| e.to_string())?
                .into_iter()
                .map(|r| r.path)
                .collect(),
        };
        list.into_iter().map(PathBuf::from).collect()
    };

    let _ = app.emit(
        "library:progress",
        LibraryScanProgressDto {
            scanning: true,
            scanned: 0,
            total: 0,
            current_file: Some("Scanning library...".to_string()),
        },
    );

    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let result = run_folder_scan(&db, &folder_paths, &state.cache_root, |prog| {
        let _ = app.emit(
            "library:progress",
            LibraryScanProgressDto {
                scanning: true,
                scanned: prog.scanned_files,
                total: prog.total_files,
                current_file: Some(prog.current_path),
            },
        );
    });

    let _ = app.emit(
        "library:progress",
        LibraryScanProgressDto {
            scanning: false,
            scanned: result.total_files,
            total: result.total_files,
            current_file: None,
        },
    );

    let records = db.list_tracks().map_err(|e| e.to_string())?;
    Ok(hydrate_tracks(&db, &records))
}

#[tauri::command]
pub fn add_folder(state: State<'_, AppState>, path: String) -> Result<Vec<String>, String> {
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    db.add_folder(&path, unix_now_secs())
        .map_err(|e| e.to_string())?;
    let mut settings = load_app_settings(&db);
    if !settings.folders.contains(&path) {
        settings.folders.push(path);
    }
    let _ = save_app_settings_to_db(&db, &settings);
    Ok(settings.folders)
}

#[tauri::command]
pub fn remove_folder(state: State<'_, AppState>, path: String) -> Result<Vec<String>, String> {
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    db.remove_folder(&path).map_err(|e| e.to_string())?;
    let mut settings = load_app_settings(&db);
    settings.folders.retain(|f| f != &path);
    let _ = save_app_settings_to_db(&db, &settings);
    Ok(settings.folders)
}

#[tauri::command]
pub fn toggle_like_track(
    app: AppHandle,
    state: State<'_, AppState>,
    track_id: String,
    liked: bool,
) -> Result<TrackDto, String> {
    let updated = {
        let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
        db.set_track_liked(&track_id, liked, unix_now_secs())
            .map_err(|e| e.to_string())?;
        let rec = db
            .get_track_by_id(&track_id)
            .map_err(|e| e.to_string())?
            .ok_or_else(|| format!("Track not found: {track_id}"))?;
        hydrate_track(&db, &rec)
    };

    let pb = state.current_playback_state();
    if pb.current_track_id.as_deref() == Some(track_id.as_str()) {
        let _ = app.emit("playback:state", &pb);
    }
    Ok(updated)
}

#[tauri::command]
pub fn update_track_tags(
    app: AppHandle,
    state: State<'_, AppState>,
    input: TagUpdateInput,
) -> Result<TrackDto, String> {
    let updated = {
        let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
        let mut rec = db
            .get_track_by_id(&input.track_id)
            .map_err(|e| e.to_string())?
            .ok_or_else(|| format!("Track not found: {}", input.track_id))?;

        let meta_update = MetadataUpdate {
            title: Some(input.title.clone()),
            artist: Some(input.artist.clone()),
            album: Some(input.album.clone()),
            album_artist: Some(input.album_artist.clone()),
            genre: Some(input.genre.clone()),
            year: input.year,
            track_num: input.track_num,
            embedded_lyrics: None,
            cover_art: None,
        };
        let path = PathBuf::from(&rec.path);
        if path.exists() {
            write_metadata(&path, &meta_update).map_err(|e| e.to_string())?;
        }

        rec.title = input.title;
        rec.artist = input.artist;
        rec.album = input.album;
        rec.album_artist = input.album_artist;
        rec.genre = input.genre;
        rec.year = input.year;
        rec.track_num = input.track_num;
        db.upsert_track(&rec).map_err(|e| e.to_string())?;
        hydrate_track(&db, &rec)
    };

    let pb = state.current_playback_state();
    if pb.current_track_id.as_deref() == Some(updated.id.as_str()) {
        let _ = app.emit("playback:state", &pb);
    }
    let q = state.current_queue_snapshot();
    let _ = app.emit("queue:updated", &q);
    Ok(updated)
}

#[tauri::command]
pub fn delete_track(
    app: AppHandle,
    state: State<'_, AppState>,
    track_id: String,
    delete_file: Option<bool>,
) -> Result<(), String> {
    let file_path = {
        let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
        let path = db
            .get_track_by_id(&track_id)
            .map_err(|e| e.to_string())?
            .map(|t| t.path);
        db.delete_track(&track_id).map_err(|e| e.to_string())?;
        path
    };

    if delete_file.unwrap_or(false) {
        if let Some(p) = file_path {
            let pb = PathBuf::from(p);
            if pb.exists() {
                let _ = std::fs::remove_file(pb);
            }
        }
    }

    if let Some(ref engine) = state.engine {
        let indices: Vec<usize> = {
            let qc = state.queue_cache.lock().unwrap_or_else(|e| e.into_inner());
            qc.tracks
                .iter()
                .enumerate()
                .filter_map(|(i, t)| if t.id == track_id { Some(i) } else { None })
                .collect()
        };
        if !indices.is_empty() {
            engine.remove_queue_indices(indices);
        }
    } else {
        let q = state.current_queue_snapshot();
        let _ = app.emit("queue:updated", &q);
    }
    Ok(())
}

#[tauri::command]
pub fn get_pinned_track_ids(state: State<'_, AppState>) -> Result<Vec<String>, String> {
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let raw = db
        .get_setting(SETTINGS_KEY_PINNED)
        .map_err(|e| e.to_string())?;
    Ok(raw
        .and_then(|s| serde_json::from_str::<Vec<String>>(&s).ok())
        .unwrap_or_default())
}

#[tauri::command]
pub fn toggle_pin_track(
    state: State<'_, AppState>,
    track_id: String,
) -> Result<Vec<String>, String> {
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let mut pinned: Vec<String> = db
        .get_setting(SETTINGS_KEY_PINNED)
        .map_err(|e| e.to_string())?
        .and_then(|s| serde_json::from_str::<Vec<String>>(&s).ok())
        .unwrap_or_default();

    if let Some(pos) = pinned.iter().position(|id| id == &track_id) {
        pinned.remove(pos);
    } else {
        pinned.insert(0, track_id);
    }

    let json = serde_json::to_string(&pinned).map_err(|e| e.to_string())?;
    db.set_setting(SETTINGS_KEY_PINNED, &json)
        .map_err(|e| e.to_string())?;
    Ok(pinned)
}

#[tauri::command]
pub fn get_playlists(state: State<'_, AppState>) -> Result<Vec<PlaylistDto>, String> {
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let pinned_set: HashSet<String> = db
        .get_setting(SETTINGS_KEY_PINNED_PLAYLISTS)
        .ok()
        .flatten()
        .and_then(|s| serde_json::from_str::<Vec<String>>(&s).ok())
        .unwrap_or_default()
        .into_iter()
        .collect();
    let list = db.list_playlists().map_err(|e| e.to_string())?;
    Ok(list
        .into_iter()
        .map(|p| playlist_record_to_dto(p, &pinned_set))
        .collect())
}

#[tauri::command]
pub fn create_playlist(
    state: State<'_, AppState>,
    name: String,
    track_ids: Option<Vec<String>>,
) -> Result<PlaylistDto, String> {
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let now = unix_now_ms();
    let id = format!("pl-{now}");
    let ids = track_ids.unwrap_or_default();
    db.upsert_playlist(&id, &name, "", now, now)
        .map_err(|e| e.to_string())?;
    for (pos, tid) in ids.iter().enumerate() {
        db.add_track_to_playlist(&id, tid, pos as i64, now)
            .map_err(|e| e.to_string())?;
    }
    Ok(PlaylistDto {
        id,
        name,
        track_ids: ids,
        created_at: now,
        updated_at: now,
        pinned: None,
    })
}

#[tauri::command]
pub fn add_track_to_playlist(
    state: State<'_, AppState>,
    playlist_id: String,
    track_id: String,
) -> Result<PlaylistDto, String> {
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let list = db.list_playlists().map_err(|e| e.to_string())?;
    let next_pos = list
        .iter()
        .find(|p| p.id == playlist_id)
        .map(|p| p.track_ids.len() as i64)
        .unwrap_or(0);
    db.add_track_to_playlist(&playlist_id, &track_id, next_pos, unix_now_ms())
        .map_err(|e| e.to_string())?;
    let pinned_set: HashSet<String> = db
        .get_setting(SETTINGS_KEY_PINNED_PLAYLISTS)
        .ok()
        .flatten()
        .and_then(|s| serde_json::from_str::<Vec<String>>(&s).ok())
        .unwrap_or_default()
        .into_iter()
        .collect();
    let list = db.list_playlists().map_err(|e| e.to_string())?;
    let pl = list
        .into_iter()
        .find(|p| p.id == playlist_id)
        .ok_or_else(|| format!("Playlist not found: {playlist_id}"))?;
    Ok(playlist_record_to_dto(pl, &pinned_set))
}

#[tauri::command]
pub fn delete_playlist(state: State<'_, AppState>, playlist_id: String) -> Result<(), String> {
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    db.delete_playlist(&playlist_id).map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
pub fn get_playback_state(state: State<'_, AppState>) -> Result<PlaybackStateDto, String> {
    Ok(state.current_playback_state())
}

#[tauri::command]
pub fn get_queue(state: State<'_, AppState>) -> Result<QueueSnapshotDto, String> {
    Ok(state.current_queue_snapshot())
}

#[tauri::command]
pub fn play_track(
    state: State<'_, AppState>,
    track_id: String,
    queue_track_ids: Option<Vec<String>>,
) -> Result<PlaybackStateDto, String> {
    let Some(ref engine) = state.engine else {
        return Err("Audio engine is unavailable".to_string());
    };

    let (tracks_for_queue, start_index, target_track) = {
        let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
        let target_rec = db
            .get_track_by_id(&track_id)
            .map_err(|e| e.to_string())?
            .ok_or_else(|| format!("Track not found: {track_id}"))?;
        let target_ref = track_record_to_ref(&target_rec);

        if let Some(ids) = queue_track_ids.filter(|v| !v.is_empty()) {
            let mut refs = Vec::with_capacity(ids.len());
            for id in ids {
                if let Ok(Some(rec)) = db.get_track_by_id(&id) {
                    refs.push(track_record_to_ref(&rec));
                }
            }
            let idx = refs.iter().position(|t| t.id == track_id).unwrap_or(0);
            (Some(refs), Some(idx), target_ref)
        } else {
            let qc = state.queue_cache.lock().unwrap_or_else(|e| e.into_inner());
            if let Some(existing_idx) = qc.tracks.iter().position(|t| t.id == track_id) {
                (None, Some(existing_idx), target_ref)
            } else {
                let mut refs = Vec::with_capacity(qc.tracks.len() + 1);
                refs.push(target_ref.clone());
                refs.extend(qc.tracks.iter().cloned());
                (Some(refs), Some(0), target_ref)
            }
        }
    };

    if let Some(refs) = tracks_for_queue {
        engine.set_queue(refs, start_index, true);
    } else if let Some(idx) = start_index {
        engine.play_index(idx);
    }

    let sleep_ms = state.sleep_timer_remaining_ms();
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let mut pb = snapshot_to_playback_dto(&db, &engine.snapshot(), sleep_ms);
    pb.status = PlaybackStatus::Playing;
    pb.current_track_id = Some(target_track.id.clone());
    if let Ok(Some(rec)) = db.get_track_by_id(&target_track.id) {
        pb.current_track = Some(hydrate_track(&db, &rec));
    }
    pb.duration_ms = target_track.duration_ms;
    Ok(pb)
}

#[tauri::command]
pub fn toggle_play_pause(state: State<'_, AppState>) -> Result<PlaybackStateDto, String> {
    if let Some(ref engine) = state.engine {
        engine.toggle_play_pause();
    }
    Ok(state.current_playback_state())
}

#[tauri::command]
pub fn next_track(state: State<'_, AppState>) -> Result<PlaybackStateDto, String> {
    if let Some(ref engine) = state.engine {
        engine.next();
    }
    Ok(state.current_playback_state())
}

#[tauri::command]
pub fn prev_track(state: State<'_, AppState>) -> Result<PlaybackStateDto, String> {
    if let Some(ref engine) = state.engine {
        engine.prev();
    }
    Ok(state.current_playback_state())
}

#[tauri::command]
pub fn seek(state: State<'_, AppState>, position_ms: u64) -> Result<(), String> {
    if let Some(ref engine) = state.engine {
        engine.seek_ms(position_ms);
    }
    Ok(())
}

#[tauri::command]
pub fn set_volume(state: State<'_, AppState>, volume: f64) -> Result<(), String> {
    if let Some(ref engine) = state.engine {
        let vol_u8 = (volume.clamp(0.0, 1.0) * 100.0).round() as u8;
        engine.set_volume(vol_u8);
    }
    Ok(())
}

#[tauri::command]
pub fn set_muted(state: State<'_, AppState>, muted: bool) -> Result<(), String> {
    if let Some(ref engine) = state.engine {
        engine.set_muted(muted);
    }
    Ok(())
}

#[tauri::command]
pub fn set_shuffle(state: State<'_, AppState>, shuffle: bool) -> Result<(), String> {
    if let Some(ref engine) = state.engine {
        engine.set_shuffled(shuffle);
    }
    Ok(())
}

#[tauri::command]
pub fn set_repeat(state: State<'_, AppState>, repeat: RepeatModeDto) -> Result<(), String> {
    if let Some(ref engine) = state.engine {
        engine.set_repeat_mode(repeat.into());
    }
    Ok(())
}

#[tauri::command]
pub fn reorder_queue(
    state: State<'_, AppState>,
    from_index: usize,
    to_index: usize,
) -> Result<QueueSnapshotDto, String> {
    if let Some(ref engine) = state.engine {
        engine.move_queue_item(from_index, to_index);
    }
    let mut qc = state.queue_cache.lock().unwrap_or_else(|e| e.into_inner());
    if from_index < qc.tracks.len() && to_index < qc.tracks.len() && from_index != to_index {
        let current_id = qc
            .current_index
            .and_then(|i| qc.tracks.get(i))
            .map(|t| t.id.clone());
        let item = qc.tracks.remove(from_index);
        qc.tracks.insert(to_index, item);
        qc.current_index = current_id.and_then(|id| qc.tracks.iter().position(|t| t.id == id));
    }
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    Ok(build_queue_dto(
        &db,
        &qc.tracks,
        qc.current_index,
        qc.shuffled,
        qc.repeat_mode,
    ))
}

#[tauri::command]
pub fn remove_from_queue(
    state: State<'_, AppState>,
    indices: Vec<usize>,
) -> Result<QueueSnapshotDto, String> {
    if let Some(ref engine) = state.engine {
        engine.remove_queue_indices(indices.clone());
    }
    let mut qc = state.queue_cache.lock().unwrap_or_else(|e| e.into_inner());
    let remove_set: HashSet<usize> = indices.into_iter().collect();
    let current_id = qc
        .current_index
        .and_then(|i| qc.tracks.get(i))
        .map(|t| t.id.clone());
    let mut new_tracks = Vec::with_capacity(qc.tracks.len());
    for (idx, tr) in qc.tracks.iter().enumerate() {
        if !remove_set.contains(&idx) {
            new_tracks.push(tr.clone());
        }
    }
    qc.tracks = new_tracks;
    qc.current_index = current_id.and_then(|id| qc.tracks.iter().position(|t| t.id == id));
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    Ok(build_queue_dto(
        &db,
        &qc.tracks,
        qc.current_index,
        qc.shuffled,
        qc.repeat_mode,
    ))
}

#[tauri::command]
pub fn enqueue_tracks(
    state: State<'_, AppState>,
    track_ids: Vec<String>,
    play_next: Option<bool>,
) -> Result<QueueSnapshotDto, String> {
    let refs: Vec<TrackRef> = {
        let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
        track_ids
            .iter()
            .filter_map(|id| db.get_track_by_id(id).ok().flatten())
            .map(|rec| track_record_to_ref(&rec))
            .collect()
    };

    let play_next = play_next.unwrap_or(false);
    if let Some(ref engine) = state.engine {
        if play_next {
            for r in refs.iter().rev() {
                engine.play_next(r.clone());
            }
        } else {
            for r in &refs {
                engine.add_to_queue(r.clone());
            }
        }
    }

    let mut qc = state.queue_cache.lock().unwrap_or_else(|e| e.into_inner());
    if play_next {
        let insert_at = qc
            .current_index
            .map(|i| (i + 1).min(qc.tracks.len()))
            .unwrap_or(0);
        for (offset, r) in refs.into_iter().enumerate() {
            qc.tracks.insert(insert_at + offset, r);
        }
    } else {
        qc.tracks.extend(refs);
    }

    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    Ok(build_queue_dto(
        &db,
        &qc.tracks,
        qc.current_index,
        qc.shuffled,
        qc.repeat_mode,
    ))
}

#[tauri::command]
pub fn set_sleep_timer(
    app: AppHandle,
    state: State<'_, AppState>,
    minutes: Option<u64>,
) -> Result<(), String> {
    {
        let mut guard = state.sleep_timer_deadline.lock().unwrap_or_else(|e| e.into_inner());
        *guard = match minutes {
            Some(m) if m > 0 => Some(
                Instant::now() + std::time::Duration::from_secs(m.saturating_mul(60)),
            ),
            _ => None,
        };
    }
    let pb = state.current_playback_state();
    let _ = app.emit("playback:state", &pb);
    Ok(())
}

#[tauri::command]
pub async fn get_lyrics(
    state: State<'_, AppState>,
    track_id: String,
) -> Result<SpicyLyricsDto, String> {
    let query = {
        let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
        let rec = db
            .get_track_by_id(&track_id)
            .map_err(|e| e.to_string())?
            .ok_or_else(|| format!("Track not found: {track_id}"))?;
        let settings = load_app_settings(&db);
        LyricsQuery {
            title: rec.title,
            artist: rec.artist,
            album: if rec.album.is_empty() {
                None
            } else {
                Some(rec.album)
            },
            duration_ms: Some(rec.duration_ms),
            track_path: Some(PathBuf::from(rec.path)),
            embedded_lyrics: None,
            api_key: if settings.spicy_lyrics_api_key.is_empty() {
                None
            } else {
                Some(settings.spicy_lyrics_api_key)
            },
        }
    };
    let parsed = state.lyrics_service.fetch_lyrics(&query).await;
    Ok(convert_parsed_lyrics(track_id, parsed))
}

#[tauri::command]
pub async fn refresh_lyrics(
    state: State<'_, AppState>,
    track_id: String,
) -> Result<SpicyLyricsDto, String> {
    let query = {
        let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
        let rec = db
            .get_track_by_id(&track_id)
            .map_err(|e| e.to_string())?
            .ok_or_else(|| format!("Track not found: {track_id}"))?;
        let settings = load_app_settings(&db);
        LyricsQuery {
            title: rec.title,
            artist: rec.artist,
            album: if rec.album.is_empty() {
                None
            } else {
                Some(rec.album)
            },
            duration_ms: Some(rec.duration_ms),
            track_path: Some(PathBuf::from(rec.path)),
            embedded_lyrics: None,
            api_key: if settings.spicy_lyrics_api_key.is_empty() {
                None
            } else {
                Some(settings.spicy_lyrics_api_key)
            },
        }
    };
    state.lyrics_service.invalidate_cache(&query);
    let parsed = state.lyrics_service.fetch_lyrics(&query).await;
    Ok(convert_parsed_lyrics(track_id, parsed))
}

#[tauri::command]
pub fn set_equalizer_config(
    state: State<'_, AppState>,
    config: EqualizerConfigDto,
) -> Result<(), String> {
    if let Some(ref engine) = state.engine {
        apply_eq_to_engine(engine, &config);
    }
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let mut settings = load_app_settings(&db);
    settings.equalizer = config;
    save_app_settings_to_db(&db, &settings)
}

#[tauri::command]
pub fn get_eq_presets(state: State<'_, AppState>) -> Result<Vec<EqualizerPresetDto>, String> {
    let mut presets = built_in_eq_presets();
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let custom = db.list_eq_presets().map_err(|e| e.to_string())?;
    for p in custom {
        if !p.is_builtin && !presets.iter().any(|b| b.id == p.id) {
            presets.push(EqualizerPresetDto {
                id: p.id,
                name: p.name,
                preamp_db: p.preamp_db,
                bands: p.bands_db,
                built_in: Some(false),
            });
        }
    }
    Ok(presets)
}

#[tauri::command]
pub fn save_eq_preset(
    state: State<'_, AppState>,
    preset: SaveEqPresetInput,
) -> Result<EqualizerPresetDto, String> {
    let id = format!("eq-{}", unix_now_ms());
    let record = EqPresetRecord {
        id: id.clone(),
        name: preset.name.clone(),
        preamp_db: preset.preamp_db,
        bands_db: preset.bands.clone(),
        is_builtin: false,
    };
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    db.upsert_eq_preset(&record).map_err(|e| e.to_string())?;
    Ok(EqualizerPresetDto {
        id,
        name: preset.name,
        preamp_db: preset.preamp_db,
        bands: preset.bands,
        built_in: Some(false),
    })
}

#[tauri::command]
pub fn set_crossfade_config(
    state: State<'_, AppState>,
    config: CrossfadeConfigDto,
) -> Result<(), String> {
    if let Some(ref engine) = state.engine {
        apply_crossfade_to_engine(engine, &config);
    }
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let mut settings = load_app_settings(&db);
    settings.crossfade = config;
    save_app_settings_to_db(&db, &settings)
}

#[tauri::command]
pub fn find_duplicates(state: State<'_, AppState>) -> Result<Vec<DuplicateGroupDto>, String> {
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let tracks = db.list_tracks().map_err(|e| e.to_string())?;
    let groups = DuplicateDetector::find_duplicates(&tracks, 2000);
    Ok(groups
        .into_iter()
        .map(|g| DuplicateGroupDto {
            id: g.key,
            reason: match g.kind {
                DuplicateMatchKind::ExactFileHash => "exact_hash".to_string(),
                DuplicateMatchKind::ExactPath | DuplicateMatchKind::MetadataAndDuration => {
                    "metadata_duration".to_string()
                }
            },
            tracks: hydrate_tracks(&db, &g.tracks),
        })
        .collect())
}

#[tauri::command]
pub fn get_listening_stats(state: State<'_, AppState>) -> Result<ListeningStatsDto, String> {
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    let summary = db.listening_stats(5).map_err(|e| e.to_string())?;
    let all_records = db.list_tracks().map_err(|e| e.to_string())?;
    let hydrated = hydrate_tracks(&db, &all_records);
    let by_id: HashMap<String, TrackDto> =
        hydrated.iter().map(|t| (t.id.clone(), t.clone())).collect();

    let top_tracks = summary
        .top_track_ids
        .into_iter()
        .filter_map(|(id, play_count)| {
            by_id.get(&id).map(|track| TopTrackStatDto {
                total_ms: play_count * track.duration_ms,
                play_count,
                track: track.clone(),
            })
        })
        .collect();

    let mut artist_map: HashMap<String, (i64, i64)> = HashMap::new();
    let mut album_map: HashMap<(String, String), (Option<String>, Option<String>, i64)> =
        HashMap::new();

    for t in &hydrated {
        if t.play_count > 0 {
            let entry = artist_map.entry(t.artist.clone()).or_insert((0, 0));
            entry.0 += t.play_count;
            entry.1 += t.play_count * t.duration_ms;

            let album_artist = if t.album_artist.is_empty() {
                t.artist.clone()
            } else {
                t.album_artist.clone()
            };
            let alb = album_map
                .entry((t.album.clone(), album_artist))
                .or_insert((t.artwork_path.clone(), t.accent_color.clone(), 0));
            alb.2 += t.play_count;
        }
    }

    let mut top_artists: Vec<TopArtistStatDto> = artist_map
        .into_iter()
        .map(|(artist, (play_count, total_ms))| TopArtistStatDto {
            artist,
            play_count,
            total_ms,
        })
        .collect();
    top_artists.sort_by(|a, b| b.play_count.cmp(&a.play_count));
    top_artists.truncate(5);

    let mut top_albums: Vec<TopAlbumStatDto> = album_map
        .into_iter()
        .map(
            |((album, artist), (artwork_path, accent_color, play_count))| TopAlbumStatDto {
                album,
                artist,
                artwork_path,
                accent_color,
                play_count,
            },
        )
        .collect();
    top_albums.sort_by(|a, b| b.play_count.cmp(&a.play_count));
    top_albums.truncate(4);

    let recent_plays = db
        .list_recent_plays(6)
        .unwrap_or_default()
        .into_iter()
        .filter_map(|entry| {
            by_id.get(&entry.track_id).map(|track| RecentPlayDto {
                track: track.clone(),
                played_at: entry.played_at,
            })
        })
        .collect();

    Ok(ListeningStatsDto {
        total_play_time_ms: summary.total_duration_ms,
        total_plays: summary.total_plays,
        tracks_count: hydrated.len(),
        top_tracks,
        top_artists,
        top_albums,
        recent_plays,
    })
}

#[tauri::command]
pub fn get_settings(state: State<'_, AppState>) -> Result<AppSettingsDto, String> {
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    Ok(load_app_settings(&db))
}

#[tauri::command]
pub fn save_settings(
    state: State<'_, AppState>,
    mut settings: AppSettingsDto,
) -> Result<AppSettingsDto, String> {
    settings.playback_rate = if settings.playback_rate.is_finite() {
        settings
            .playback_rate
            .clamp(MIN_PLAYBACK_RATE, MAX_PLAYBACK_RATE)
    } else {
        1.0
    };
    if let Some(ref engine) = state.engine {
        apply_eq_to_engine(engine, &settings.equalizer);
        apply_crossfade_to_engine(engine, &settings.crossfade);
        apply_playback_rate_to_engine(engine, settings.playback_rate);
    }
    let db = state.db.lock().unwrap_or_else(|e| e.into_inner());
    save_app_settings_to_db(&db, &settings)?;
    Ok(settings)
}

#[tauri::command]
pub fn toggle_mini_player(app: AppHandle, open: Option<bool>) -> Result<(), String> {
    let win = app
        .get_webview_window("mini-player")
        .ok_or_else(|| "mini-player window not found".to_string())?;

    let should_show = match open {
        Some(v) => v,
        None => !win.is_visible().unwrap_or(false),
    };

    if should_show {
        win.show().map_err(|e| e.to_string())?;
        let _ = win.set_focus();
    } else {
        win.hide().map_err(|e| e.to_string())?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_app_settings_roundtrip_and_legacy_defaults() {
        let db = Database::open_in_memory().unwrap();

        let defaults = load_app_settings(&db);
        assert!(!defaults.minimize_to_tray);
        assert!(!defaults.close_to_tray);
        assert!(defaults.startup_intro);
        assert!((defaults.playback_rate - 1.0).abs() < f32::EPSILON);

        let custom = AppSettingsDto {
            minimize_to_tray: true,
            close_to_tray: true,
            startup_intro: false,
            playback_rate: 1.5,
            ..AppSettingsDto::default()
        };
        save_app_settings_to_db(&db, &custom).unwrap();

        let loaded = load_app_settings(&db);
        assert!(loaded.minimize_to_tray);
        assert!(loaded.close_to_tray);
        assert!(!loaded.startup_intro);
        assert!((loaded.playback_rate - 1.5).abs() < f32::EPSILON);

        // Legacy JSON missing new fields should still deserialize with defaults.
        let legacy_json = r#"{
            "theme": "light",
            "adaptiveAccent": false,
            "folders": [],
            "spicyLyricsApiKey": "",
            "crossfade": { "enabled": false, "durationMs": 3000, "gapless": true },
            "equalizer": { "enabled": false, "preampDb": 0.0, "bands": [0,0,0,0,0,0,0,0,0,0], "presetName": "Flat" },
            "keybindings": {
                "playPause": "Space",
                "nextTrack": "ArrowRight",
                "prevTrack": "ArrowLeft",
                "volumeUp": "ArrowUp",
                "volumeDown": "ArrowDown",
                "toggleMute": "KeyM",
                "toggleLyrics": "KeyL",
                "toggleQueue": "KeyQ",
                "focusSearch": "KeyF"
            },
            "minimizeToTray": true,
            "showMiniPlayerOnClose": false
        }"#;
        db.set_setting(SETTINGS_KEY_APP, legacy_json).unwrap();
        let migrated = load_app_settings(&db);
        assert_eq!(migrated.theme, "light");
        assert!(migrated.minimize_to_tray);
        assert!(!migrated.close_to_tray);
        assert!(migrated.startup_intro);
        assert!((migrated.playback_rate - 1.0).abs() < f32::EPSILON);
    }
}
