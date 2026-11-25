use std::fs::File;
use std::path::Path;
use std::time::Duration;
use symphonia::core::audio::{SampleBuffer, SignalSpec};
use symphonia::core::codecs::{Decoder, DecoderOptions, CODEC_TYPE_NULL};
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::{FormatOptions, FormatReader, SeekMode, SeekTo};
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;
use symphonia::core::units::Time;
use thiserror::Error;

/// Errors produced by [`AudioDecoder`].
#[derive(Debug, Error)]
pub enum DecoderError {
    #[error("failed to open audio file: {0}")]
    Io(#[from] std::io::Error),
    #[error("no supported audio track found")]
    NoAudioTrack,
    #[error("missing stream audio parameters")]
    MissingParameters,
    #[error("symphonia error: {0}")]
    Symphonia(#[from] SymphoniaError),
}

/// Decoded audio stream metadata.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct AudioSpec {
    pub sample_rate: u32,
    pub channels: usize,
    pub duration_ms: u64,
}

/// Streaming `symphonia` audio decoder supporting MP3, FLAC, AAC/M4A, ALAC,
/// OGG Vorbis, and WAV, outputting interleaved `f32` PCM frames.
pub struct AudioDecoder {
    format: Box<dyn FormatReader>,
    decoder: Box<dyn Decoder>,
    track_id: u32,
    spec: AudioSpec,
    sample_buf: Option<SampleBuffer<f32>>,
    sample_buf_spec: Option<SignalSpec>,
    current_frame: u64,
    eof: bool,
}

impl AudioDecoder {
    /// Open and probe an audio file at `path`.
    pub fn open<P: AsRef<Path>>(path: P) -> Result<Self, DecoderError> {
        let path = path.as_ref();
        let file = File::open(path)?;
        let mss = MediaSourceStream::new(Box::new(file), Default::default());

        let mut hint = Hint::new();
        if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
            hint.with_extension(ext);
        }

        let probed = symphonia::default::get_probe().format(
            &hint,
            mss,
            &FormatOptions {
                enable_gapless: true,
                ..Default::default()
            },
            &MetadataOptions::default(),
        )?;

        let format = probed.format;
        let track = format
            .tracks()
            .iter()
            .find(|t| t.codec_params.codec != CODEC_TYPE_NULL)
            .ok_or(DecoderError::NoAudioTrack)?;

        let track_id = track.id;
        let params = &track.codec_params;
        let sample_rate = params
            .sample_rate
            .filter(|&sr| sr > 0)
            .ok_or(DecoderError::MissingParameters)?;
        let channels = params
            .channels
            .map(|c| c.count())
            .filter(|&c| c > 0)
            .unwrap_or(2);

        let duration_ms = match (params.n_frames, params.time_base) {
            (Some(n_frames), Some(tb)) => {
                let time = tb.calc_time(n_frames);
                time.seconds
                    .saturating_mul(1000)
                    .saturating_add((time.frac * 1000.0).round() as u64)
            }
            (Some(n_frames), None) => {
                n_frames.saturating_mul(1000) / u64::from(sample_rate.max(1))
            }
            _ => 0,
        };

        let decoder =
            symphonia::default::get_codecs().make(params, &DecoderOptions::default())?;

        Ok(Self {
            format,
            decoder,
            track_id,
            spec: AudioSpec {
                sample_rate,
                channels,
                duration_ms,
            },
            sample_buf: None,
            sample_buf_spec: None,
            current_frame: 0,
            eof: false,
        })
    }

    pub fn spec(&self) -> AudioSpec {
        self.spec
    }

    pub fn sample_rate(&self) -> u32 {
        self.spec.sample_rate
    }

    pub fn channels(&self) -> usize {
        self.spec.channels
    }

    pub fn duration_ms(&self) -> u64 {
        self.spec.duration_ms
    }

    pub fn position_ms(&self) -> u64 {
        let sr = u64::from(self.spec.sample_rate.max(1));
        (self.current_frame * 1000) / sr
    }

    pub fn is_eof(&self) -> bool {
        self.eof
    }

    /// Seek to `position_ms` in the stream.
    pub fn seek_ms(&mut self, position_ms: u64) -> Result<u64, DecoderError> {
        let clamped_ms = if self.spec.duration_ms > 0 {
            position_ms.min(self.spec.duration_ms)
        } else {
            position_ms
        };
        let target_time = Time::from(Duration::from_millis(clamped_ms));
        let seeked = self.format.seek(
            SeekMode::Accurate,
            SeekTo::Time {
                time: target_time,
                track_id: Some(self.track_id),
            },
        )?;
        self.decoder.reset();
        self.eof = false;

        // Update current_frame from actual seek timestamp if available.
        let actual_ms = if let Some(tb) = self
            .format
            .tracks()
            .iter()
            .find(|t| t.id == self.track_id)
            .and_then(|t| t.codec_params.time_base)
        {
            let t = tb.calc_time(seeked.actual_ts);
            t.seconds * 1000 + (t.frac * 1000.0).round() as u64
        } else {
            clamped_ms
        };
        self.current_frame =
            actual_ms.saturating_mul(u64::from(self.spec.sample_rate)) / 1000;
        Ok(actual_ms)
    }

    /// Decode the next packet into interleaved `f32` samples appended to `out`.
    /// Returns the number of samples appended (`0` indicates EOF).
    pub fn next_interleaved_samples(&mut self, out: &mut Vec<f32>) -> Result<usize, DecoderError> {
        if self.eof {
            return Ok(0);
        }

        loop {
            let packet = match self.format.next_packet() {
                Ok(p) => p,
                Err(SymphoniaError::IoError(err))
                    if err.kind() == std::io::ErrorKind::UnexpectedEof =>
                {
                    self.eof = true;
                    return Ok(0);
                }
                Err(SymphoniaError::ResetRequired) => {
                    self.decoder.reset();
                    continue;
                }
                Err(e) => return Err(DecoderError::Symphonia(e)),
            };

            if packet.track_id() != self.track_id {
                continue;
            }

            match self.decoder.decode(&packet) {
                Ok(decoded) => {
                    let spec = *decoded.spec();
                    let frames = decoded.frames();
                    let ch_count = spec.channels.count();
                    if frames == 0 || ch_count == 0 {
                        continue;
                    }
                    let needed_samples = frames * ch_count;
                    let alloc_frames = frames.max(decoded.capacity()) as u64;
                    let needs_realloc = self.sample_buf_spec != Some(spec)
                        || self
                            .sample_buf
                            .as_ref()
                            .is_none_or(|b| b.capacity() < needed_samples);
                    if needs_realloc {
                        self.sample_buf = Some(SampleBuffer::<f32>::new(alloc_frames, spec));
                        self.sample_buf_spec = Some(spec);
                    }
                    let Some(buf) = self.sample_buf.as_mut() else {
                        continue;
                    };
                    buf.copy_interleaved_ref(decoded);
                    let samples = buf.samples();
                    out.extend_from_slice(samples);
                    self.current_frame += frames as u64;
                    return Ok(samples.len());
                }
                Err(SymphoniaError::DecodeError(_)) => {
                    // Skip corrupt frame and continue decoding.
                    continue;
                }
                Err(SymphoniaError::IoError(err))
                    if err.kind() == std::io::ErrorKind::UnexpectedEof =>
                {
                    self.eof = true;
                    return Ok(0);
                }
                Err(e) => return Err(DecoderError::Symphonia(e)),
            }
        }
    }
}
