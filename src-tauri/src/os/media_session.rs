use crate::audio::engine::PlaybackStatus;
use souvlaki::{
    MediaControlEvent, MediaControls, MediaMetadata, MediaPlayback, MediaPosition, PlatformConfig,
    SeekDirection,
};
use std::ffi::c_void;
use std::path::Path;
use std::time::Duration;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum MediaSessionError {
    #[error("failed to initialize OS media controls: {0}")]
    Init(String),
    #[error("failed to update OS media controls: {0}")]
    Update(String),
}

/// Normalized OS media control events (Windows SMTC, macOS Now Playing, Linux MPRIS2).
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum MediaSessionEvent {
    Play,
    Pause,
    Toggle,
    Next,
    Previous,
    Stop,
    SeekByMs(i64),
    SetPositionMs(u64),
}

/// Cross-platform OS media session wrapper backed by `souvlaki`.
pub struct MediaSession {
    controls: MediaControls,
}

impl MediaSession {
    /// Initialize OS media controls and attach the event handler.
    pub fn new<F>(
        display_name: &str,
        dbus_name: &str,
        hwnd: Option<*mut c_void>,
        on_event: F,
    ) -> Result<Self, MediaSessionError>
    where
        F: Fn(MediaSessionEvent) + Send + Sync + 'static,
    {
        #[cfg(target_os = "windows")]
        if hwnd.is_none() {
            return Err(MediaSessionError::Init(
                "Windows media controls require a valid HWND".to_string(),
            ));
        }

        let config = PlatformConfig {
            dbus_name,
            display_name,
            hwnd,
        };
        let mut controls =
            MediaControls::new(config).map_err(|e| MediaSessionError::Init(format!("{e:?}")))?;

        controls
            .attach(move |event: MediaControlEvent| {
                let mapped = match event {
                    MediaControlEvent::Play => Some(MediaSessionEvent::Play),
                    MediaControlEvent::Pause => Some(MediaSessionEvent::Pause),
                    MediaControlEvent::Toggle => Some(MediaSessionEvent::Toggle),
                    MediaControlEvent::Next => Some(MediaSessionEvent::Next),
                    MediaControlEvent::Previous => Some(MediaSessionEvent::Previous),
                    MediaControlEvent::Stop => Some(MediaSessionEvent::Stop),
                    MediaControlEvent::Seek(dir) => Some(MediaSessionEvent::SeekByMs(match dir {
                        SeekDirection::Forward => 10_000,
                        SeekDirection::Backward => -10_000,
                    })),
                    MediaControlEvent::SeekBy(dir, dur) => {
                        let ms = dur.as_millis() as i64;
                        Some(MediaSessionEvent::SeekByMs(match dir {
                            SeekDirection::Forward => ms,
                            SeekDirection::Backward => -ms,
                        }))
                    }
                    MediaControlEvent::SetPosition(MediaPosition(dur)) => {
                        Some(MediaSessionEvent::SetPositionMs(dur.as_millis() as u64))
                    }
                    _ => None,
                };
                if let Some(ev) = mapped {
                    on_event(ev);
                }
            })
            .map_err(|e| MediaSessionError::Init(format!("{e:?}")))?;

        Ok(Self { controls })
    }

    /// Update now-playing metadata shown in the OS media overlay.
    pub fn set_metadata(
        &mut self,
        title: Option<&str>,
        artist: Option<&str>,
        album: Option<&str>,
        duration_ms: Option<u64>,
        cover_url: Option<&str>,
    ) -> Result<(), MediaSessionError> {
        let normalized_cover = normalize_cover_url(cover_url);
        let metadata = MediaMetadata {
            title,
            artist,
            album,
            duration: duration_ms.map(Duration::from_millis),
            cover_url: normalized_cover.as_deref(),
        };
        self.controls
            .set_metadata(metadata)
            .map_err(|e| MediaSessionError::Update(format!("{e:?}")))
    }

    /// Update playback status and optional position.
    pub fn set_playback(
        &mut self,
        status: PlaybackStatus,
        position_ms: Option<u64>,
    ) -> Result<(), MediaSessionError> {
        let progress = position_ms.map(|ms| MediaPosition(Duration::from_millis(ms)));
        let playback = match status {
            PlaybackStatus::Stopped => MediaPlayback::Stopped,
            PlaybackStatus::Paused => MediaPlayback::Paused { progress },
            PlaybackStatus::Playing => MediaPlayback::Playing { progress },
        };
        self.controls
            .set_playback(playback)
            .map_err(|e| MediaSessionError::Update(format!("{e:?}")))
    }

    /// Detach OS media controls.
    pub fn detach(&mut self) -> Result<(), MediaSessionError> {
        self.controls
            .detach()
            .map_err(|e| MediaSessionError::Update(format!("{e:?}")))
    }
}

/// Convert a local filesystem path or URL string into a valid, percent-encoded
/// `file://` or `http(s)://` URL for `souvlaki`. Returns `None` for empty strings
/// or non-existent local files so `NSImage` / SMTC never receives an invalid URL.
fn normalize_cover_url(raw: Option<&str>) -> Option<String> {
    let s = raw?.trim();
    if s.is_empty() {
        return None;
    }
    if s.starts_with("http://") || s.starts_with("https://") || s.starts_with("file://") {
        return Some(s.to_string());
    }

    let path = Path::new(s);
    if !path.is_file() {
        return None;
    }

    let canonical = path.canonicalize().unwrap_or_else(|_| path.to_path_buf());
    let path_str = canonical.to_string_lossy();
    let normalized = path_str.replace('\\', "/");

    let mut out = String::from("file://");
    if !normalized.starts_with('/') {
        out.push('/');
    }
    for byte in normalized.as_bytes() {
        match *byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' | b'/' | b':' => {
                out.push(*byte as char);
            }
            b => {
                use std::fmt::Write;
                let _ = write!(out, "%{b:02X}");
            }
        }
    }
    Some(out)
}
