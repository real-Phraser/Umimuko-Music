pub mod audio;
pub mod commands;
pub mod library;
pub mod lyrics;
pub mod os;

use crate::audio::engine::{AudioEngine, AudioEvent, PlaybackStatus};
use crate::audio::queue::RepeatMode;
use crate::commands::{
    apply_crossfade_to_engine, apply_eq_to_engine, build_queue_dto, load_app_settings,
    sleep_remaining_ms, state_event_to_playback_dto, AppState, PlaybackTickPayload, QueueCache,
};
use crate::library::db::{app_data_dir, Database};
use crate::library::migration::migrate_legacy_v0261;
use crate::lyrics::service::SpicyLyricsService;
use crate::os::media_session::{MediaSession, MediaSessionEvent};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use tauri::{Emitter, Manager};

fn unix_now_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .setup(|app| {
            let data_dir = app_data_dir().unwrap_or_else(|_| {
                app.path()
                    .app_data_dir()
                    .unwrap_or_else(|_| PathBuf::from(".umimuko"))
            });
            let _ = std::fs::create_dir_all(&data_dir);

            let db_path = data_dir.join("library.db");
            let db = Database::open_at(&db_path).expect("failed to open library database");
            if let Err(err) = migrate_legacy_v0261(&db) {
                tracing::warn!("Legacy v0.26.1 migration skipped or failed: {err}");
            }

            let initial_settings = load_app_settings(&db);
            let cache_root = data_dir.join("cache");
            let _ = std::fs::create_dir_all(&cache_root);

            let db_arc = Arc::new(Mutex::new(db));
            let media_session_arc: Arc<Mutex<Option<MediaSession>>> = Arc::new(Mutex::new(None));
            let sleep_deadline: Arc<Mutex<Option<Instant>>> = Arc::new(Mutex::new(None));
            let queue_cache: Arc<Mutex<QueueCache>> = Arc::new(Mutex::new(QueueCache::default()));

            let app_handle = app.handle().clone();
            let db_ev = Arc::clone(&db_arc);
            let ms_ev = Arc::clone(&media_session_arc);
            let sleep_ev = Arc::clone(&sleep_deadline);
            let qc_ev = Arc::clone(&queue_cache);
            let last_recorded_track_id: Arc<Mutex<Option<String>>> = Arc::new(Mutex::new(None));
            let last_media_track_id: Arc<Mutex<Option<String>>> = Arc::new(Mutex::new(None));

            let engine = match AudioEngine::new(Arc::new(move |ev: AudioEvent| match ev {
                AudioEvent::PlaybackTick {
                    position_ms,
                    duration_ms,
                    ..
                } => {
                    let _ = app_handle.emit(
                        "playback:tick",
                        PlaybackTickPayload {
                            position_ms,
                            duration_ms,
                        },
                    );
                }
                AudioEvent::PlaybackState {
                    status,
                    track,
                    position_ms,
                    duration_ms,
                    volume,
                    muted,
                    ..
                } => {
                    if status == PlaybackStatus::Playing {
                        if let Some(ref tr) = track {
                            let mut last = last_recorded_track_id.lock().unwrap();
                            if last.as_deref() != Some(tr.id.as_str()) {
                                *last = Some(tr.id.clone());
                                let db = db_ev.lock().unwrap();
                                let _ = db.record_play(
                                    &tr.id,
                                    unix_now_ms(),
                                    tr.duration_ms as i64,
                                );
                            }
                        }
                    }

                    if let Ok(mut ms_guard) = ms_ev.lock() {
                        if let Some(ref mut ms) = *ms_guard {
                            let track_id = track.as_ref().map(|t| t.id.clone());
                            let mut last_ms_id = last_media_track_id.lock().unwrap();
                            if *last_ms_id != track_id {
                                *last_ms_id = track_id;
                                if let Some(ref tr) = track {
                                    let _ = ms.set_metadata(
                                        Some(&tr.title),
                                        Some(&tr.artist),
                                        Some(&tr.album),
                                        Some(tr.duration_ms),
                                        tr.artwork_path.as_deref(),
                                    );
                                }
                            }
                            let _ = ms.set_playback(status, Some(position_ms));
                        }
                    }

                    let (shuffled, repeat_mode) = {
                        let qc = qc_ev.lock().unwrap();
                        (qc.shuffled, qc.repeat_mode)
                    };
                    let sleep_ms = sleep_remaining_ms(&sleep_ev);
                    let dto = {
                        let db = db_ev.lock().unwrap();
                        state_event_to_playback_dto(
                            &db,
                            status,
                            track.as_ref(),
                            position_ms,
                            duration_ms,
                            volume,
                            muted,
                            shuffled,
                            repeat_mode,
                            sleep_ms,
                        )
                    };
                    let _ = app_handle.emit("playback:state", &dto);
                }
                AudioEvent::QueueUpdated {
                    tracks,
                    current_index,
                    repeat_mode,
                    shuffled,
                } => {
                    {
                        let mut qc = qc_ev.lock().unwrap();
                        qc.tracks = tracks.clone();
                        qc.current_index = current_index;
                        qc.repeat_mode = repeat_mode;
                        qc.shuffled = shuffled;
                    }
                    let dto = {
                        let db = db_ev.lock().unwrap();
                        build_queue_dto(&db, &tracks, current_index, shuffled, repeat_mode)
                    };
                    let _ = app_handle.emit("queue:updated", &dto);
                }
                AudioEvent::Error { message } => {
                    tracing::error!("AudioEngine error: {message}");
                }
            })) {
                Ok(eng) => {
                    apply_eq_to_engine(&eng, &initial_settings.equalizer);
                    apply_crossfade_to_engine(&eng, &initial_settings.crossfade);
                    Some(Arc::new(eng))
                }
                Err(err) => {
                    tracing::warn!("AudioEngine initialization failed (running without audio output): {err}");
                    None
                }
            };

            if let Some(ref eng) = engine {
                let eng_ms = Arc::clone(eng);
                match MediaSession::new("Umimuko Music", "com.umimuko.music", None, move |ev| {
                    match ev {
                        MediaSessionEvent::Play => eng_ms.play(),
                        MediaSessionEvent::Pause => eng_ms.pause(),
                        MediaSessionEvent::Toggle => eng_ms.toggle_play_pause(),
                        MediaSessionEvent::Next => eng_ms.next(),
                        MediaSessionEvent::Previous => eng_ms.prev(),
                        MediaSessionEvent::Stop => eng_ms.stop(),
                        MediaSessionEvent::SeekByMs(delta) => {
                            let snap = eng_ms.snapshot();
                            let next_pos = (snap.position_ms as i64 + delta).max(0) as u64;
                            eng_ms.seek_ms(next_pos);
                        }
                        MediaSessionEvent::SetPositionMs(pos) => eng_ms.seek_ms(pos),
                    }
                }) {
                    Ok(ms) => {
                        *media_session_arc.lock().unwrap() = Some(ms);
                    }
                    Err(err) => {
                        tracing::warn!("MediaSession initialization failed: {err}");
                    }
                }

                // Background sleep timer monitor
                let eng_sleep = Arc::clone(eng);
                let sleep_bg = Arc::clone(&sleep_deadline);
                let app_sleep = app.handle().clone();
                let db_sleep = Arc::clone(&db_arc);
                let qc_sleep = Arc::clone(&queue_cache);
                std::thread::Builder::new()
                    .name("umimuko-sleep-timer".to_string())
                    .spawn(move || loop {
                        std::thread::sleep(Duration::from_secs(1));
                        let expired = {
                            let mut guard = sleep_bg.lock().unwrap();
                            if let Some(dl) = *guard {
                                if Instant::now() >= dl {
                                    *guard = None;
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        };
                        if expired {
                            eng_sleep.pause();
                            let snap = eng_sleep.snapshot();
                            let (shuffled, repeat_mode) = {
                                let qc = qc_sleep.lock().unwrap();
                                (qc.shuffled, qc.repeat_mode)
                            };
                            let dto = {
                                let db = db_sleep.lock().unwrap();
                                state_event_to_playback_dto(
                                    &db,
                                    PlaybackStatus::Paused,
                                    snap.current_track.as_ref(),
                                    snap.position_ms,
                                    snap.duration_ms,
                                    snap.volume,
                                    snap.muted,
                                    shuffled,
                                    repeat_mode,
                                    None,
                                )
                            };
                            let _ = app_sleep.emit("playback:state", &dto);
                        }
                    })
                    .ok();
            } else {
                // Keep unused imports clean when engine is None
                let _ = RepeatMode::Off;
            }

            let lyrics_service = SpicyLyricsService::new(&cache_root);

            app.manage(AppState {
                db: db_arc,
                engine,
                media_session: media_session_arc,
                lyrics_service,
                cache_root,
                sleep_timer_deadline: sleep_deadline,
                queue_cache,
            });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::get_tracks,
            commands::scan_folders,
            commands::add_folder,
            commands::remove_folder,
            commands::toggle_like_track,
            commands::update_track_tags,
            commands::delete_track,
            commands::get_pinned_track_ids,
            commands::toggle_pin_track,
            commands::get_playlists,
            commands::create_playlist,
            commands::add_track_to_playlist,
            commands::delete_playlist,
            commands::get_playback_state,
            commands::get_queue,
            commands::play_track,
            commands::toggle_play_pause,
            commands::next_track,
            commands::prev_track,
            commands::seek,
            commands::set_volume,
            commands::set_muted,
            commands::set_shuffle,
            commands::set_repeat,
            commands::reorder_queue,
            commands::remove_from_queue,
            commands::enqueue_tracks,
            commands::set_sleep_timer,
            commands::get_lyrics,
            commands::refresh_lyrics,
            commands::set_equalizer_config,
            commands::get_eq_presets,
            commands::save_eq_preset,
            commands::set_crossfade_config,
            commands::find_duplicates,
            commands::get_listening_stats,
            commands::get_settings,
            commands::save_settings,
            commands::toggle_mini_player,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
