use crate::audio::decoder::AudioDecoder;
use crate::audio::dsp::{CrossfadeCurve, CrossfadeMath, EqConfig, EqPreset, Equalizer};
use crate::audio::queue::{EditableQueue, RepeatMode, TrackRef};
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use ringbuf::traits::{Consumer, Observer, Producer, Split};
use ringbuf::{HeapCons, HeapProd, HeapRb};
use serde::{Deserialize, Serialize};
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, AtomicU8, Ordering};
use std::sync::{mpsc, Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};
use thiserror::Error;

pub const MIN_PLAYBACK_RATE: f32 = 0.5;
pub const MAX_PLAYBACK_RATE: f32 = 2.0;

#[derive(Debug, Error)]
pub enum EngineError {
    #[error("no default audio output device available")]
    NoOutputDevice,
    #[error("audio device error: {0}")]
    Device(String),
    #[error("decoder error: {0}")]
    Decoder(String),
}

/// High-level playback state.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum PlaybackStatus {
    #[default]
    Stopped,
    Playing,
    Paused,
}

/// Events emitted by [`AudioEngine`] for UI / OS media session sync.
#[derive(Clone, Debug, Serialize)]
#[serde(tag = "event", content = "payload")]
pub enum AudioEvent {
    #[serde(rename = "playback:tick")]
    PlaybackTick {
        track_id: Option<String>,
        position_ms: u64,
        duration_ms: u64,
    },
    #[serde(rename = "playback:state")]
    PlaybackState {
        status: PlaybackStatus,
        track: Option<TrackRef>,
        position_ms: u64,
        duration_ms: u64,
        volume: u8,
        muted: bool,
        playback_rate: f32,
    },
    #[serde(rename = "queue:updated")]
    QueueUpdated {
        tracks: Vec<TrackRef>,
        current_index: Option<usize>,
        repeat_mode: RepeatMode,
        shuffled: bool,
    },
    #[serde(rename = "playback:error")]
    Error { message: String },
}

pub type EventCallback = Arc<dyn Fn(AudioEvent) + Send + Sync + 'static>;

enum EngineCommand {
    Play,
    Pause,
    TogglePlayPause,
    Stop,
    SeekMs(u64),
    Next,
    Prev,
    PlayIndex(usize),
    SetQueue {
        tracks: Vec<TrackRef>,
        start_index: Option<usize>,
        autoplay: bool,
    },
    MoveQueueItem {
        from: usize,
        to: usize,
    },
    RemoveQueueIndices(Vec<usize>),
    PlayNext(TrackRef),
    AddToQueue(TrackRef),
    ClearQueue,
    SetRepeatMode(RepeatMode),
    SetShuffled(bool),
    SetVolume(u8),
    SetMuted(bool),
    SetPlaybackRate(f32),
    SetEqConfig(EqConfig),
    ApplyEqPreset(EqPreset),
    SetCrossfade {
        duration_secs: f32,
        curve: CrossfadeCurve,
    },
    Shutdown,
}

struct SharedAudioState {
    playing: AtomicBool,
    muted: AtomicBool,
    volume_bits: AtomicU32,
    crossfade_active: AtomicBool,
    crossfade_curve: AtomicU8,
    crossfade_pos_frames: AtomicU64,
    crossfade_total_frames: AtomicU64,
    flush_a: AtomicBool,
    flush_b: AtomicBool,
    played_frames_a: AtomicU64,
    played_frames_b: AtomicU64,
}

impl SharedAudioState {
    fn new() -> Self {
        Self {
            playing: AtomicBool::new(false),
            muted: AtomicBool::new(false),
            volume_bits: AtomicU32::new(1.0_f32.to_bits()),
            crossfade_active: AtomicBool::new(false),
            crossfade_curve: AtomicU8::new(1), // EqualPower
            crossfade_pos_frames: AtomicU64::new(0),
            crossfade_total_frames: AtomicU64::new(0),
            flush_a: AtomicBool::new(false),
            flush_b: AtomicBool::new(false),
            played_frames_a: AtomicU64::new(0),
            played_frames_b: AtomicU64::new(0),
        }
    }

    fn set_volume_linear(&self, gain: f32) {
        self.volume_bits
            .store(gain.clamp(0.0, 1.0).to_bits(), Ordering::Relaxed);
    }

    fn volume_linear(&self) -> f32 {
        f32::from_bits(self.volume_bits.load(Ordering::Relaxed))
    }
}

/// Snapshot of engine state for synchronous queries.
#[derive(Clone, Debug, Serialize)]
pub struct EngineSnapshot {
    pub status: PlaybackStatus,
    pub current_track: Option<TrackRef>,
    pub current_index: Option<usize>,
    pub position_ms: u64,
    pub duration_ms: u64,
    pub volume: u8,
    pub muted: bool,
    pub playback_rate: f32,
    pub repeat_mode: RepeatMode,
    pub shuffled: bool,
    pub crossfade_secs: f32,
    pub crossfade_curve: CrossfadeCurve,
}

/// Dual-stream `cpal` + lock-free `ringbuf` audio engine with gapless playback,
/// crossfade, 10-band EQ, volume/mute, and playback rate control.
pub struct AudioEngine {
    cmd_tx: mpsc::Sender<EngineCommand>,
    snapshot: Arc<Mutex<EngineSnapshot>>,
    _worker_thread: Option<thread::JoinHandle<()>>,
    _audio_thread: Option<thread::JoinHandle<()>>,
}

impl AudioEngine {
    /// Start the audio engine with an event callback.
    pub fn new(on_event: EventCallback) -> Result<Self, EngineError> {
        let (cmd_tx, cmd_rx) = mpsc::channel::<EngineCommand>();
        let shared = Arc::new(SharedAudioState::new());

        let snapshot = Arc::new(Mutex::new(EngineSnapshot {
            status: PlaybackStatus::Stopped,
            current_track: None,
            current_index: None,
            position_ms: 0,
            duration_ms: 0,
            volume: 100,
            muted: false,
            playback_rate: 1.0,
            repeat_mode: RepeatMode::Off,
            shuffled: false,
            crossfade_secs: 0.0,
            crossfade_curve: CrossfadeCurve::EqualPower,
        }));

        // Negotiate output device format on a dedicated audio host thread
        // (keeps !Send cpal::Stream alive safely across all OS backends).
        let (fmt_tx, fmt_rx) = mpsc::channel::<Result<(u32, usize), EngineError>>();
        let rb_capacity_default = 48000 * 2 * 4; // ~4s stereo 48kHz
        let rb_a = HeapRb::<f32>::new(rb_capacity_default);
        let rb_b = HeapRb::<f32>::new(rb_capacity_default);
        let (prod_a, cons_a) = rb_a.split();
        let (prod_b, cons_b) = rb_b.split();

        let shared_audio = Arc::clone(&shared);
        let audio_thread = thread::Builder::new()
            .name("umimuko-cpal-output".to_string())
            .spawn(move || {
                run_cpal_host(shared_audio, cons_a, cons_b, fmt_tx);
            })
            .map_err(|e| EngineError::Device(e.to_string()))?;

        let (sample_rate, channels) = fmt_rx
            .recv()
            .map_err(|e| EngineError::Device(e.to_string()))??;

        let snapshot_worker = Arc::clone(&snapshot);
        let shared_worker = Arc::clone(&shared);
        let worker_thread = thread::Builder::new()
            .name("umimuko-audio-decoder".to_string())
            .spawn(move || {
                run_decoder_worker(
                    cmd_rx,
                    shared_worker,
                    prod_a,
                    prod_b,
                    sample_rate,
                    channels,
                    snapshot_worker,
                    on_event,
                );
            })
            .map_err(|e| EngineError::Device(e.to_string()))?;

        Ok(Self {
            cmd_tx,
            snapshot,
            _worker_thread: Some(worker_thread),
            _audio_thread: Some(audio_thread),
        })
    }

    pub fn snapshot(&self) -> EngineSnapshot {
        self.snapshot.lock().unwrap().clone()
    }

    pub fn play(&self) {
        let _ = self.cmd_tx.send(EngineCommand::Play);
    }

    pub fn pause(&self) {
        let _ = self.cmd_tx.send(EngineCommand::Pause);
    }

    pub fn toggle_play_pause(&self) {
        let _ = self.cmd_tx.send(EngineCommand::TogglePlayPause);
    }

    pub fn stop(&self) {
        let _ = self.cmd_tx.send(EngineCommand::Stop);
    }

    pub fn seek_ms(&self, position_ms: u64) {
        let _ = self.cmd_tx.send(EngineCommand::SeekMs(position_ms));
    }

    pub fn next(&self) {
        let _ = self.cmd_tx.send(EngineCommand::Next);
    }

    pub fn prev(&self) {
        let _ = self.cmd_tx.send(EngineCommand::Prev);
    }

    pub fn play_index(&self, index: usize) {
        let _ = self.cmd_tx.send(EngineCommand::PlayIndex(index));
    }

    pub fn set_queue(&self, tracks: Vec<TrackRef>, start_index: Option<usize>, autoplay: bool) {
        let _ = self.cmd_tx.send(EngineCommand::SetQueue {
            tracks,
            start_index,
            autoplay,
        });
    }

    pub fn move_queue_item(&self, from: usize, to: usize) {
        let _ = self.cmd_tx.send(EngineCommand::MoveQueueItem { from, to });
    }

    pub fn remove_queue_indices(&self, indices: Vec<usize>) {
        let _ = self.cmd_tx.send(EngineCommand::RemoveQueueIndices(indices));
    }

    pub fn play_next(&self, track: TrackRef) {
        let _ = self.cmd_tx.send(EngineCommand::PlayNext(track));
    }

    pub fn add_to_queue(&self, track: TrackRef) {
        let _ = self.cmd_tx.send(EngineCommand::AddToQueue(track));
    }

    pub fn clear_queue(&self) {
        let _ = self.cmd_tx.send(EngineCommand::ClearQueue);
    }

    pub fn set_repeat_mode(&self, mode: RepeatMode) {
        let _ = self.cmd_tx.send(EngineCommand::SetRepeatMode(mode));
    }

    pub fn set_shuffled(&self, shuffled: bool) {
        let _ = self.cmd_tx.send(EngineCommand::SetShuffled(shuffled));
    }

    pub fn set_volume(&self, volume: u8) {
        let _ = self.cmd_tx.send(EngineCommand::SetVolume(volume.min(100)));
    }

    pub fn set_muted(&self, muted: bool) {
        let _ = self.cmd_tx.send(EngineCommand::SetMuted(muted));
    }

    pub fn set_playback_rate(&self, rate: f32) {
        let _ = self.cmd_tx.send(EngineCommand::SetPlaybackRate(
            rate.clamp(MIN_PLAYBACK_RATE, MAX_PLAYBACK_RATE),
        ));
    }

    pub fn set_eq_config(&self, config: EqConfig) {
        let _ = self.cmd_tx.send(EngineCommand::SetEqConfig(config));
    }

    pub fn apply_eq_preset(&self, preset: EqPreset) {
        let _ = self.cmd_tx.send(EngineCommand::ApplyEqPreset(preset));
    }

    pub fn set_crossfade(&self, duration_secs: f32, curve: CrossfadeCurve) {
        let _ = self.cmd_tx.send(EngineCommand::SetCrossfade {
            duration_secs: CrossfadeMath::clamp_duration_secs(duration_secs),
            curve,
        });
    }
}

impl Drop for AudioEngine {
    fn drop(&mut self) {
        let _ = self.cmd_tx.send(EngineCommand::Shutdown);
    }
}

fn run_cpal_host(
    shared: Arc<SharedAudioState>,
    mut cons_a: HeapCons<f32>,
    mut cons_b: HeapCons<f32>,
    fmt_tx: mpsc::Sender<Result<(u32, usize), EngineError>>,
) {
    let host = cpal::default_host();
    let Some(device) = host.default_output_device() else {
        let _ = fmt_tx.send(Err(EngineError::NoOutputDevice));
        return;
    };

    let supported_config = match device.default_output_config() {
        Ok(cfg) => cfg,
        Err(e) => {
            let _ = fmt_tx.send(Err(EngineError::Device(e.to_string())));
            return;
        }
    };

    let sample_rate = supported_config.sample_rate().0;
    let channels = supported_config.channels() as usize;
    let config: cpal::StreamConfig = supported_config.into();

    let stream = device.build_output_stream(
        &config,
        move |output: &mut [f32], _: &cpal::OutputCallbackInfo| {
            if shared.flush_a.swap(false, Ordering::AcqRel) {
                cons_a.clear();
                shared.played_frames_a.store(0, Ordering::Relaxed);
            }
            if shared.flush_b.swap(false, Ordering::AcqRel) {
                cons_b.clear();
                shared.played_frames_b.store(0, Ordering::Relaxed);
            }

            if !shared.playing.load(Ordering::Acquire) {
                output.fill(0.0);
                return;
            }

            let gain = if shared.muted.load(Ordering::Acquire) {
                0.0
            } else {
                shared.volume_linear()
            };

            let xfade = shared.crossfade_active.load(Ordering::Acquire);
            let total_xfade = shared.crossfade_total_frames.load(Ordering::Relaxed);
            let curve = if shared.crossfade_curve.load(Ordering::Relaxed) == 0 {
                CrossfadeCurve::Linear
            } else {
                CrossfadeCurve::EqualPower
            };

            let ch = channels.max(1);
            let mut frames_a = 0u64;
            let mut frames_b = 0u64;

            for frame in output.chunks_mut(ch) {
                let mut xfade_g = (1.0_f32, 0.0_f32);
                if xfade && total_xfade > 0 {
                    let pos = shared.crossfade_pos_frames.fetch_add(1, Ordering::Relaxed);
                    let progress = (pos as f32 / total_xfade as f32).clamp(0.0, 1.0);
                    xfade_g = CrossfadeMath::gains(curve, progress);
                    if pos + 1 >= total_xfade {
                        shared.crossfade_active.store(false, Ordering::Release);
                    }
                }

                let mut got_a = false;
                let mut got_b = false;
                for sample_out in frame.iter_mut() {
                    let a = match cons_a.try_pop() {
                        Some(v) => {
                            got_a = true;
                            v
                        }
                        None => 0.0,
                    };
                    let mixed = if xfade || cons_b.occupied_len() > 0 {
                        let b = match cons_b.try_pop() {
                            Some(v) => {
                                got_b = true;
                                v
                            }
                            None => 0.0,
                        };
                        a * xfade_g.0 + b * xfade_g.1
                    } else {
                        a
                    };
                    *sample_out = (mixed * gain).clamp(-1.0, 1.0);
                }
                if got_a {
                    frames_a += 1;
                }
                if got_b {
                    frames_b += 1;
                }
            }

            if frames_a > 0 {
                shared.played_frames_a.fetch_add(frames_a, Ordering::Relaxed);
            }
            if frames_b > 0 {
                shared.played_frames_b.fetch_add(frames_b, Ordering::Relaxed);
            }
        },
        |err| {
            tracing::error!("cpal output stream error: {err}");
        },
        None,
    );

    let stream = match stream {
        Ok(s) => s,
        Err(e) => {
            let _ = fmt_tx.send(Err(EngineError::Device(e.to_string())));
            return;
        }
    };

    if let Err(e) = stream.play() {
        let _ = fmt_tx.send(Err(EngineError::Device(e.to_string())));
        return;
    }

    let _ = fmt_tx.send(Ok((sample_rate, channels)));

    // Keep stream alive until process exit or channel drop.
    loop {
        thread::sleep(Duration::from_millis(250));
    }
}

struct ActiveStream {
    decoder: AudioDecoder,
    track_id: String,
    duration_ms: u64,
    base_pos_ms: u64,
    resample_buf: Vec<f32>,
    resample_cursor: f64,
    eq: Equalizer,
    eof_reached: bool,
}

impl ActiveStream {
    fn open(
        track: &TrackRef,
        device_sr: u32,
        device_ch: usize,
        eq_config: &EqConfig,
    ) -> Result<Self, String> {
        let decoder = AudioDecoder::open(&track.path).map_err(|e| e.to_string())?;
        let duration_ms = if decoder.duration_ms() > 0 {
            decoder.duration_ms()
        } else {
            track.duration_ms
        };
        let mut eq = Equalizer::new(device_sr, device_ch);
        eq.apply_config(eq_config);
        Ok(Self {
            decoder,
            track_id: track.id.clone(),
            duration_ms,
            base_pos_ms: 0,
            resample_buf: Vec::new(),
            resample_cursor: 0.0,
            eq,
            eof_reached: false,
        })
    }

    /// Decode and resample into `device_sr`/`device_ch` at `playback_rate`, applying EQ.
    fn fill_producer(&mut self, prod: &mut HeapProd<f32>, device_sr: u32, device_ch: usize, playback_rate: f32) {
        let free = prod.vacant_len();
        if free < device_ch * 256 {
            return;
        }

        let src_sr = self.decoder.sample_rate().max(1);
        let src_ch = self.decoder.channels().max(1);
        let step = (src_sr as f64 / device_sr as f64) * (playback_rate as f64);

        let mut raw = Vec::new();
        while !self.eof_reached && self.resample_buf.len() < src_ch * 2048 {
            raw.clear();
            match self.decoder.next_interleaved_samples(&mut raw) {
                Ok(0) => {
                    self.eof_reached = true;
                    break;
                }
                Ok(_) => {
                    self.resample_buf.extend_from_slice(&raw);
                }
                Err(e) => {
                    tracing::warn!("decode error: {e}");
                    self.eof_reached = true;
                    break;
                }
            }
        }

        let available_src_frames = self.resample_buf.len() / src_ch;
        if available_src_frames < 2 {
            return;
        }

        let max_out_frames = free / device_ch;
        let mut out_samples = Vec::with_capacity(max_out_frames.min(4096) * device_ch);
        let limit_frames = max_out_frames.min(4096);

        for _ in 0..limit_frames {
            let idx0 = self.resample_cursor.floor() as usize;
            let idx1 = idx0 + 1;
            if idx1 >= available_src_frames {
                break;
            }
            let frac = (self.resample_cursor - idx0 as f64) as f32;
            for c in 0..device_ch {
                let sc = c.min(src_ch - 1);
                let s0 = self.resample_buf[idx0 * src_ch + sc];
                let s1 = self.resample_buf[idx1 * src_ch + sc];
                out_samples.push(s0 + (s1 - s0) * frac);
            }
            self.resample_cursor += step;
        }

        let consumed_src_frames = self.resample_cursor.floor() as usize;
        if consumed_src_frames > 0 {
            let drain_samples = consumed_src_frames * src_ch;
            if drain_samples <= self.resample_buf.len() {
                self.resample_buf.drain(..drain_samples);
                self.resample_cursor -= consumed_src_frames as f64;
            }
        }

        if !out_samples.is_empty() {
            self.eq.process_interleaved(&mut out_samples);
            prod.push_slice(&out_samples);
        }
    }
}

#[allow(clippy::too_many_arguments)]
fn run_decoder_worker(
    cmd_rx: mpsc::Receiver<EngineCommand>,
    shared: Arc<SharedAudioState>,
    mut prod_a: HeapProd<f32>,
    mut prod_b: HeapProd<f32>,
    device_sr: u32,
    device_ch: usize,
    snapshot: Arc<Mutex<EngineSnapshot>>,
    on_event: EventCallback,
) {
    let mut queue = EditableQueue::new();
    let mut status = PlaybackStatus::Stopped;
    let mut volume: u8 = 100;
    let mut muted = false;
    let mut playback_rate: f32 = 1.0;
    let mut eq_config = EqConfig::default();
    let mut crossfade_secs: f32 = 0.0;
    let mut crossfade_curve = CrossfadeCurve::EqualPower;

    let mut primary: Option<ActiveStream> = None;
    let mut secondary: Option<ActiveStream> = None;
    let mut last_tick = Instant::now();

    let emit_state = |status: PlaybackStatus,
                      queue: &EditableQueue,
                      pos_ms: u64,
                      dur_ms: u64,
                      volume: u8,
                      muted: bool,
                      rate: f32,
                      xfade_secs: f32,
                      xfade_curve: CrossfadeCurve| {
        let track = queue.current_track().cloned();
        {
            let mut snap = snapshot.lock().unwrap();
            snap.status = status;
            snap.current_track = track.clone();
            snap.current_index = queue.current_index();
            snap.position_ms = pos_ms;
            snap.duration_ms = dur_ms;
            snap.volume = volume;
            snap.muted = muted;
            snap.playback_rate = rate;
            snap.repeat_mode = queue.repeat_mode();
            snap.shuffled = queue.is_shuffled();
            snap.crossfade_secs = xfade_secs;
            snap.crossfade_curve = xfade_curve;
        }
        on_event(AudioEvent::PlaybackState {
            status,
            track,
            position_ms: pos_ms,
            duration_ms: dur_ms,
            volume,
            muted,
            playback_rate: rate,
        });
    };

    let emit_queue = |queue: &EditableQueue| {
        on_event(AudioEvent::QueueUpdated {
            tracks: queue.tracks().to_vec(),
            current_index: queue.current_index(),
            repeat_mode: queue.repeat_mode(),
            shuffled: queue.is_shuffled(),
        });
    };

    loop {
        while let Ok(cmd) = cmd_rx.try_recv() {
            match cmd {
                EngineCommand::Shutdown => return,
                EngineCommand::Play => {
                    if primary.is_none() {
                        if let Some(track) = queue.current_track().cloned() {
                            match ActiveStream::open(&track, device_sr, device_ch, &eq_config) {
                                Ok(s) => primary = Some(s),
                                Err(msg) => {
                                    on_event(AudioEvent::Error { message: msg });
                                    continue;
                                }
                            }
                        }
                    }
                    if primary.is_some() {
                        status = PlaybackStatus::Playing;
                        shared.playing.store(true, Ordering::Release);
                        let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                        emit_state(status, &queue, pos, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                    }
                }
                EngineCommand::Pause => {
                    status = PlaybackStatus::Paused;
                    shared.playing.store(false, Ordering::Release);
                    let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                    emit_state(status, &queue, pos, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                }
                EngineCommand::TogglePlayPause => {
                    if status == PlaybackStatus::Playing {
                        status = PlaybackStatus::Paused;
                        shared.playing.store(false, Ordering::Release);
                    } else if primary.is_some() || queue.current_track().is_some() {
                        if primary.is_none() {
                            if let Some(track) = queue.current_track().cloned() {
                                if let Ok(s) = ActiveStream::open(&track, device_sr, device_ch, &eq_config) {
                                    primary = Some(s);
                                }
                            }
                        }
                        if primary.is_some() {
                            status = PlaybackStatus::Playing;
                            shared.playing.store(true, Ordering::Release);
                        }
                    }
                    let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                    emit_state(status, &queue, pos, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                }
                EngineCommand::Stop => {
                    status = PlaybackStatus::Stopped;
                    shared.playing.store(false, Ordering::Release);
                    shared.flush_a.store(true, Ordering::Release);
                    shared.flush_b.store(true, Ordering::Release);
                    shared.crossfade_active.store(false, Ordering::Release);
                    primary = None;
                    secondary = None;
                    emit_state(status, &queue, 0, 0, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                }
                EngineCommand::SeekMs(ms) => {
                    if let Some(stream) = primary.as_mut() {
                        match stream.decoder.seek_ms(ms) {
                            Ok(actual_ms) => {
                                stream.base_pos_ms = actual_ms;
                                stream.resample_buf.clear();
                                stream.resample_cursor = 0.0;
                                stream.eof_reached = false;
                                stream.eq.reset_state();
                                shared.flush_a.store(true, Ordering::Release);
                                shared.played_frames_a.store(0, Ordering::Relaxed);
                                let dur = stream.duration_ms;
                                emit_state(status, &queue, actual_ms, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                            }
                            Err(e) => on_event(AudioEvent::Error {
                                message: format!("seek failed: {e}"),
                            }),
                        }
                    }
                }
                EngineCommand::Next => {
                    if let Some(track) = queue.advance_next(true).cloned() {
                        load_track_as_primary(
                            &track,
                            &mut primary,
                            &mut secondary,
                            &shared,
                            device_sr,
                            device_ch,
                            &eq_config,
                            &mut status,
                            true,
                            &on_event,
                        );
                        emit_queue(&queue);
                        let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                        emit_state(status, &queue, pos, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                    }
                }
                EngineCommand::Prev => {
                    let (pos, _) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                    if pos > 3000 {
                        if let Some(stream) = primary.as_mut() {
                            if let Ok(actual_ms) = stream.decoder.seek_ms(0) {
                                stream.base_pos_ms = actual_ms;
                                stream.resample_buf.clear();
                                stream.resample_cursor = 0.0;
                                shared.flush_a.store(true, Ordering::Release);
                                shared.played_frames_a.store(0, Ordering::Relaxed);
                            }
                        }
                    } else if let Some(track) = queue.advance_prev().cloned() {
                        let was_playing = status == PlaybackStatus::Playing;
                        load_track_as_primary(
                            &track,
                            &mut primary,
                            &mut secondary,
                            &shared,
                            device_sr,
                            device_ch,
                            &eq_config,
                            &mut status,
                            was_playing,
                            &on_event,
                        );
                        emit_queue(&queue);
                    }
                    let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                    emit_state(status, &queue, pos, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                }
                EngineCommand::PlayIndex(index) => {
                    if let Some(track) = queue.set_current_index(index).cloned() {
                        load_track_as_primary(
                            &track,
                            &mut primary,
                            &mut secondary,
                            &shared,
                            device_sr,
                            device_ch,
                            &eq_config,
                            &mut status,
                            true,
                            &on_event,
                        );
                        emit_queue(&queue);
                        let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                        emit_state(status, &queue, pos, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                    }
                }
                EngineCommand::SetQueue {
                    tracks,
                    start_index,
                    autoplay,
                } => {
                    queue.set_tracks(tracks, start_index);
                    emit_queue(&queue);
                    if let Some(track) = queue.current_track().cloned() {
                        load_track_as_primary(
                            &track,
                            &mut primary,
                            &mut secondary,
                            &shared,
                            device_sr,
                            device_ch,
                            &eq_config,
                            &mut status,
                            autoplay,
                            &on_event,
                        );
                    } else {
                        status = PlaybackStatus::Stopped;
                        shared.playing.store(false, Ordering::Release);
                        primary = None;
                        secondary = None;
                    }
                    let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                    emit_state(status, &queue, pos, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                }
                EngineCommand::MoveQueueItem { from, to } => {
                    if queue.move_item(from, to) {
                        emit_queue(&queue);
                    }
                }
                EngineCommand::RemoveQueueIndices(indices) => {
                    if queue.remove_indices(&indices) > 0 {
                        emit_queue(&queue);
                    }
                }
                EngineCommand::PlayNext(track) => {
                    queue.play_next(track);
                    emit_queue(&queue);
                }
                EngineCommand::AddToQueue(track) => {
                    queue.add_to_queue(track);
                    emit_queue(&queue);
                }
                EngineCommand::ClearQueue => {
                    queue.clear();
                    status = PlaybackStatus::Stopped;
                    shared.playing.store(false, Ordering::Release);
                    shared.flush_a.store(true, Ordering::Release);
                    shared.flush_b.store(true, Ordering::Release);
                    primary = None;
                    secondary = None;
                    emit_queue(&queue);
                    emit_state(status, &queue, 0, 0, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                }
                EngineCommand::SetRepeatMode(mode) => {
                    queue.set_repeat_mode(mode);
                    emit_queue(&queue);
                }
                EngineCommand::SetShuffled(shuffled) => {
                    queue.set_shuffled(shuffled);
                    emit_queue(&queue);
                }
                EngineCommand::SetVolume(v) => {
                    volume = v.min(100);
                    shared.set_volume_linear(f32::from(volume) / 100.0);
                    let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                    emit_state(status, &queue, pos, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                }
                EngineCommand::SetMuted(m) => {
                    muted = m;
                    shared.muted.store(m, Ordering::Release);
                    let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                    emit_state(status, &queue, pos, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                }
                EngineCommand::SetPlaybackRate(rate) => {
                    playback_rate = rate.clamp(MIN_PLAYBACK_RATE, MAX_PLAYBACK_RATE);
                    let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                    emit_state(status, &queue, pos, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                }
                EngineCommand::SetEqConfig(cfg) => {
                    eq_config = cfg;
                    if let Some(s) = primary.as_mut() {
                        s.eq.apply_config(&eq_config);
                    }
                    if let Some(s) = secondary.as_mut() {
                        s.eq.apply_config(&eq_config);
                    }
                }
                EngineCommand::ApplyEqPreset(preset) => {
                    eq_config = EqConfig::from_preset(preset);
                    if let Some(s) = primary.as_mut() {
                        s.eq.apply_config(&eq_config);
                    }
                    if let Some(s) = secondary.as_mut() {
                        s.eq.apply_config(&eq_config);
                    }
                }
                EngineCommand::SetCrossfade {
                    duration_secs,
                    curve,
                } => {
                    crossfade_secs = CrossfadeMath::clamp_duration_secs(duration_secs);
                    crossfade_curve = curve;
                    shared.crossfade_curve.store(
                        match curve {
                            CrossfadeCurve::Linear => 0,
                            CrossfadeCurve::EqualPower => 1,
                        },
                        Ordering::Relaxed,
                    );
                }
            }
        }

        if status == PlaybackStatus::Playing {
            let mut primary_finished = false;
            if let Some(p) = primary.as_mut() {
                p.fill_producer(&mut prod_a, device_sr, device_ch, playback_rate);

                // Trigger crossfade if near end of track and crossfade is enabled.
                if crossfade_secs > 0.0 && secondary.is_none() && p.duration_ms > 0 {
                    let (pos_ms, dur_ms) = current_pos_dur_ref(p, &shared, device_sr, playback_rate);
                    let remaining_ms = dur_ms.saturating_sub(pos_ms);
                    let xfade_ms = (crossfade_secs * 1000.0) as u64;
                    if remaining_ms <= xfade_ms {
                        if let Some(next_idx) = queue.peek_next_auto() {
                            if let Some(next_track) = queue.tracks().get(next_idx).cloned() {
                                if let Ok(mut sec) =
                                    ActiveStream::open(&next_track, device_sr, device_ch, &eq_config)
                                {
                                    sec.fill_producer(&mut prod_b, device_sr, device_ch, playback_rate);
                                    let total_frames = ((crossfade_secs * device_sr as f32) as u64).max(1);
                                    shared.crossfade_total_frames.store(total_frames, Ordering::Relaxed);
                                    shared.crossfade_pos_frames.store(0, Ordering::Relaxed);
                                    shared.crossfade_active.store(true, Ordering::Release);
                                    secondary = Some(sec);
                                }
                            }
                        }
                    }
                }

                if let Some(sec) = secondary.as_mut() {
                    sec.fill_producer(&mut prod_b, device_sr, device_ch, playback_rate);
                }

                primary_finished = p.eof_reached && prod_a.is_empty();
            }

            if primary_finished {
                if let Some(sec) = secondary.take() {
                    let _ = queue.advance_next(false);
                    primary = Some(sec);
                    // Swap/align frame counters cleanly.
                    let b_frames = shared.played_frames_b.swap(0, Ordering::Relaxed);
                    shared.played_frames_a.store(b_frames, Ordering::Relaxed);
                    shared.crossfade_active.store(false, Ordering::Release);
                    emit_queue(&queue);
                    let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                    emit_state(status, &queue, pos, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                } else if let Some(next_track) = queue.advance_next(false).cloned() {
                    // Gapless transition
                    load_track_as_primary(
                        &next_track,
                        &mut primary,
                        &mut secondary,
                        &shared,
                        device_sr,
                        device_ch,
                        &eq_config,
                        &mut status,
                        true,
                        &on_event,
                    );
                    emit_queue(&queue);
                    let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                    emit_state(status, &queue, pos, dur, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                } else {
                    // End of queue
                    status = PlaybackStatus::Stopped;
                    shared.playing.store(false, Ordering::Release);
                    primary = None;
                    emit_state(status, &queue, 0, 0, volume, muted, playback_rate, crossfade_secs, crossfade_curve);
                }
            }

            if last_tick.elapsed() >= Duration::from_millis(100) {
                last_tick = Instant::now();
                let (pos, dur) = current_pos_dur(&primary, &shared, device_sr, playback_rate);
                let track_id = primary.as_ref().map(|p| p.track_id.clone());
                {
                    let mut snap = snapshot.lock().unwrap();
                    snap.position_ms = pos;
                    snap.duration_ms = dur;
                }
                on_event(AudioEvent::PlaybackTick {
                    track_id,
                    position_ms: pos,
                    duration_ms: dur,
                });
            }
        }

        thread::sleep(Duration::from_millis(10));
    }
}

#[allow(clippy::too_many_arguments)]
fn load_track_as_primary(
    track: &TrackRef,
    primary: &mut Option<ActiveStream>,
    secondary: &mut Option<ActiveStream>,
    shared: &Arc<SharedAudioState>,
    device_sr: u32,
    device_ch: usize,
    eq_config: &EqConfig,
    status: &mut PlaybackStatus,
    autoplay: bool,
    on_event: &EventCallback,
) {
    shared.flush_a.store(true, Ordering::Release);
    shared.flush_b.store(true, Ordering::Release);
    shared.crossfade_active.store(false, Ordering::Release);
    shared.played_frames_a.store(0, Ordering::Relaxed);
    shared.played_frames_b.store(0, Ordering::Relaxed);
    *secondary = None;

    match ActiveStream::open(track, device_sr, device_ch, eq_config) {
        Ok(stream) => {
            *primary = Some(stream);
            *status = if autoplay {
                PlaybackStatus::Playing
            } else {
                PlaybackStatus::Paused
            };
            shared.playing.store(autoplay, Ordering::Release);
        }
        Err(msg) => {
            *primary = None;
            *status = PlaybackStatus::Stopped;
            shared.playing.store(false, Ordering::Release);
            on_event(AudioEvent::Error { message: msg });
        }
    }
}

fn current_pos_dur_ref(
    p: &ActiveStream,
    shared: &SharedAudioState,
    device_sr: u32,
    playback_rate: f32,
) -> (u64, u64) {
    let frames = shared.played_frames_a.load(Ordering::Relaxed);
    let elapsed_ms =
        ((frames as f64 / f64::from(device_sr.max(1))) * 1000.0 * f64::from(playback_rate)) as u64;
    let pos = (p.base_pos_ms + elapsed_ms).min(p.duration_ms.max(p.base_pos_ms + elapsed_ms));
    (pos, p.duration_ms)
}

fn current_pos_dur(
    primary: &Option<ActiveStream>,
    shared: &SharedAudioState,
    device_sr: u32,
    playback_rate: f32,
) -> (u64, u64) {
    match primary {
        Some(p) => current_pos_dur_ref(p, shared, device_sr, playback_rate),
        None => (0, 0),
    }
}
