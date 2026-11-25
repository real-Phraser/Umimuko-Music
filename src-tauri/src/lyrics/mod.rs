pub mod parser;
pub mod service;

pub use parser::{
    parse_timestamp_ms, LyricsKind, LyricsLine, LyricsParseError, LyricsWord, SpicyLyrics,
    SpicyLyricsParser,
};
pub use service::{LyricsQuery, SpicyLyricsService};
