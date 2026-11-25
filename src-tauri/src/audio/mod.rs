pub mod decoder;
pub mod dsp;
pub mod engine;
pub mod queue;

pub use decoder::{AudioDecoder, AudioSpec, DecoderError};
pub use dsp::{
    CrossfadeCurve, CrossfadeMath, EqConfig, EqPreset, Equalizer, EQ_BAND_COUNT, EQ_FREQUENCIES,
};
pub use engine::{AudioEngine, AudioEvent, EngineError, EngineSnapshot, PlaybackStatus};
pub use queue::{EditableQueue, RepeatMode, TrackRef};
