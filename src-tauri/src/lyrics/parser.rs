use quick_xml::events::Event;
use quick_xml::Reader;
use serde::{Deserialize, Serialize};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum LyricsParseError {
    #[error("XML parse error: {0}")]
    Xml(#[from] quick_xml::Error),
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum LyricsKind {
    Syllable,
    Line,
    Static,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct LyricsWord {
    pub text: String,
    pub start_ms: i64,
    pub end_ms: i64,
    pub is_background: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct LyricsLine {
    pub text: String,
    pub start_ms: Option<i64>,
    pub end_ms: Option<i64>,
    pub words: Vec<LyricsWord>,
    pub background_words: Vec<LyricsWord>,
    pub romanized: Option<String>,
    pub is_background: bool,
    pub alignment: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct SpicyLyrics {
    pub kind: LyricsKind,
    pub source: String,
    pub lines: Vec<LyricsLine>,
    pub raw: String,
}

pub struct SpicyLyricsParser;

impl SpicyLyricsParser {
    /// Auto-detects TTML, LRC (standard or enhanced), or plain text lyrics.
    pub fn parse_auto(raw: &str, source: &str) -> SpicyLyrics {
        let trimmed = raw.trim();
        if trimmed.starts_with('<') && (trimmed.contains("<tt") || trimmed.contains("<p ")) {
            if let Ok(parsed) = Self::parse_ttml(trimmed, source) {
                if !parsed.lines.is_empty() {
                    return parsed;
                }
            }
        }
        if has_lrc_timestamps(trimmed) {
            let parsed = Self::parse_lrc(trimmed, source);
            if !parsed.lines.is_empty() {
                return parsed;
            }
        }
        Self::parse_plain(raw, source)
    }

    /// Parses TTML lyrics (line-level & word-level `<span>` timestamps, `ttm:role="x-bg"`
    /// background vocals, and `ttm:role="x-roman"` romanized lines).
    pub fn parse_ttml(xml: &str, source: &str) -> Result<SpicyLyrics, LyricsParseError> {
        let mut reader = Reader::from_str(xml);
        reader.config_mut().trim_text(false);

        let mut lines: Vec<LyricsLine> = Vec::new();
        let mut current_p: Option<TtmlParagraph> = None;
        let mut span_stack: Vec<TtmlSpan> = Vec::new();
        let mut has_syllables = false;

        let mut buf = Vec::new();
        loop {
            match reader.read_event_into(&mut buf)? {
                Event::Start(e) => {
                    let name_bytes = e.name().as_ref().to_vec();
                    let local = local_name(&name_bytes);
                    if local == b"p" {
                        let mut p = TtmlParagraph::default();
                        for attr in e.attributes().flatten() {
                            let k = local_name(attr.key.as_ref());
                            let val = String::from_utf8_lossy(&attr.value).to_string();
                            match k {
                                b"begin" => p.begin_ms = parse_timestamp_ms(&val),
                                b"end" => p.end_ms = parse_timestamp_ms(&val),
                                b"role" => {
                                    if val == "x-bg" {
                                        p.is_background = true;
                                    }
                                }
                                b"agent" => {
                                    if val.contains("v2") {
                                        p.alignment = Some("right".to_string());
                                    } else if val.contains("v1") {
                                        p.alignment = Some("left".to_string());
                                    }
                                }
                                _ => {}
                            }
                        }
                        current_p = Some(p);
                    } else if local == b"span" && current_p.is_some() {
                        let mut span = TtmlSpan::default();
                        for attr in e.attributes().flatten() {
                            let k = local_name(attr.key.as_ref());
                            let val = String::from_utf8_lossy(&attr.value).to_string();
                            match k {
                                b"begin" => span.begin_ms = parse_timestamp_ms(&val),
                                b"end" => span.end_ms = parse_timestamp_ms(&val),
                                b"role" => span.role = Some(val),
                                _ => {}
                            }
                        }
                        span_stack.push(span);
                    }
                }
                Event::Text(e) => {
                    if let Some(ref mut p) = current_p {
                        let text = e.unescape().map(|s| s.to_string()).unwrap_or_default();
                        if !text.is_empty() {
                            if let Some(top) = span_stack.last_mut() {
                                top.text.push_str(&text);
                            } else {
                                p.plain_text.push_str(&text);
                            }
                        }
                    }
                }
                Event::End(e) => {
                    let name_bytes = e.name().as_ref().to_vec();
                    let local = local_name(&name_bytes);
                    if local == b"span" {
                        if let Some(span) = span_stack.pop() {
                            if let Some(ref mut p) = current_p {
                                let in_bg = p.is_background
                                    || span.role.as_deref() == Some("x-bg")
                                    || span_stack.iter().any(|s| s.role.as_deref() == Some("x-bg"));
                                let in_roman = span.role.as_deref() == Some("x-roman")
                                    || span_stack
                                        .iter()
                                        .any(|s| s.role.as_deref() == Some("x-roman"));

                                if in_roman {
                                    let trimmed = span.text.trim();
                                    if !trimmed.is_empty() {
                                        match p.romanized {
                                            Some(ref mut r) => {
                                                r.push(' ');
                                                r.push_str(trimmed);
                                            }
                                            None => p.romanized = Some(trimmed.to_string()),
                                        }
                                    }
                                } else if let (Some(b), Some(end)) = (span.begin_ms, span.end_ms) {
                                    if !span.text.is_empty() {
                                        has_syllables = true;
                                        let word = LyricsWord {
                                            text: span.text.clone(),
                                            start_ms: b,
                                            end_ms: end,
                                            is_background: in_bg,
                                        };
                                        if in_bg && !p.is_background {
                                            p.background_words.push(word);
                                        } else {
                                            p.words.push(word);
                                        }
                                    }
                                } else if !span.text.trim().is_empty() {
                                    if in_bg && !p.is_background {
                                        let bg_start = p.begin_ms.unwrap_or(0);
                                        let bg_end = p.end_ms.unwrap_or(bg_start);
                                        p.background_words.push(LyricsWord {
                                            text: span.text.trim().to_string(),
                                            start_ms: bg_start,
                                            end_ms: bg_end,
                                            is_background: true,
                                        });
                                    } else {
                                        p.plain_text.push_str(&span.text);
                                    }
                                }
                            }
                        }
                    } else if local == b"p" {
                        if let Some(p) = current_p.take() {
                            span_stack.clear();
                            let text = if !p.words.is_empty() {
                                p.words.iter().map(|w| w.text.as_str()).collect::<String>()
                            } else {
                                p.plain_text.trim().to_string()
                            };
                            let text = text.trim().to_string();
                            if !text.is_empty() || !p.background_words.is_empty() {
                                lines.push(LyricsLine {
                                    text,
                                    start_ms: p.begin_ms,
                                    end_ms: p.end_ms,
                                    words: p.words,
                                    background_words: p.background_words,
                                    romanized: p.romanized,
                                    is_background: p.is_background,
                                    alignment: p.alignment,
                                });
                            }
                        }
                    }
                }
                Event::Eof => break,
                _ => {}
            }
            buf.clear();
        }

        let kind = if has_syllables {
            LyricsKind::Syllable
        } else if lines.iter().any(|l| l.start_ms.is_some()) {
            LyricsKind::Line
        } else {
            LyricsKind::Static
        };

        Ok(SpicyLyrics {
            kind,
            source: source.to_string(),
            lines,
            raw: xml.to_string(),
        })
    }

    /// Parses standard (`[mm:ss.xx]`) and enhanced (`<mm:ss.xx>`) LRC lyrics.
    pub fn parse_lrc(lrc: &str, source: &str) -> SpicyLyrics {
        let mut parsed_lines: Vec<LyricsLine> = Vec::new();
        let mut has_words = false;

        for raw_line in lrc.lines() {
            let line = raw_line.trim();
            if line.is_empty() {
                continue;
            }

            let mut rest = line;
            let mut timestamps: Vec<i64> = Vec::new();
            while rest.starts_with('[') {
                let Some(close_idx) = rest.find(']') else {
                    break;
                };
                let tag = &rest[1..close_idx];
                if let Some(ms) = parse_timestamp_ms(tag) {
                    timestamps.push(ms);
                    rest = &rest[close_idx + 1..];
                } else {
                    // Metadata tag like [ar:Artist] or [ti:Title]
                    timestamps.clear();
                    rest = "";
                    break;
                }
            }

            if timestamps.is_empty() {
                continue;
            }

            let content = rest.trim();
            let (plain_text, words) = parse_enhanced_lrc_words(content);
            if !words.is_empty() {
                has_words = true;
            }

            for ts in timestamps {
                let mut line_words = words.clone();
                if let Some(last) = line_words.last_mut() {
                    if last.end_ms <= last.start_ms {
                        last.end_ms = last.start_ms + 500;
                    }
                }
                parsed_lines.push(LyricsLine {
                    text: plain_text.clone(),
                    start_ms: Some(ts),
                    end_ms: None,
                    words: line_words,
                    background_words: Vec::new(),
                    romanized: None,
                    is_background: false,
                    alignment: None,
                });
            }
        }

        parsed_lines.sort_by_key(|l| l.start_ms.unwrap_or(0));
        for i in 0..parsed_lines.len() {
            if let Some(next_start) = parsed_lines.get(i + 1).and_then(|l| l.start_ms) {
                parsed_lines[i].end_ms = Some(next_start);
                if let Some(last_word) = parsed_lines[i].words.last_mut() {
                    if next_start > last_word.start_ms {
                        last_word.end_ms = next_start;
                    }
                }
            }
        }

        let kind = if has_words {
            LyricsKind::Syllable
        } else if !parsed_lines.is_empty() {
            LyricsKind::Line
        } else {
            LyricsKind::Static
        };

        SpicyLyrics {
            kind,
            source: source.to_string(),
            lines: parsed_lines,
            raw: lrc.to_string(),
        }
    }

    /// Parses unsynced plain-text lyrics.
    pub fn parse_plain(text: &str, source: &str) -> SpicyLyrics {
        let lines = text
            .lines()
            .map(str::trim)
            .filter(|l| !l.is_empty())
            .map(|l| LyricsLine {
                text: l.to_string(),
                start_ms: None,
                end_ms: None,
                words: Vec::new(),
                background_words: Vec::new(),
                romanized: None,
                is_background: false,
                alignment: None,
            })
            .collect();

        SpicyLyrics {
            kind: LyricsKind::Static,
            source: source.to_string(),
            lines,
            raw: text.to_string(),
        }
    }
}

#[derive(Default)]
struct TtmlParagraph {
    begin_ms: Option<i64>,
    end_ms: Option<i64>,
    is_background: bool,
    alignment: Option<String>,
    words: Vec<LyricsWord>,
    background_words: Vec<LyricsWord>,
    romanized: Option<String>,
    plain_text: String,
}

#[derive(Default)]
struct TtmlSpan {
    begin_ms: Option<i64>,
    end_ms: Option<i64>,
    role: Option<String>,
    text: String,
}

fn local_name(full: &[u8]) -> &[u8] {
    match full.iter().position(|&b| b == b':') {
        Some(idx) => &full[idx + 1..],
        None => full,
    }
}

fn has_lrc_timestamps(text: &str) -> bool {
    text.lines().any(|line| {
        let trimmed = line.trim();
        if let Some(rest) = trimmed.strip_prefix('[') {
            if let Some(close) = rest.find(']') {
                return parse_timestamp_ms(&rest[..close]).is_some();
            }
        }
        false
    })
}

fn parse_enhanced_lrc_words(content: &str) -> (String, Vec<LyricsWord>) {
    if !content.contains('<') || !content.contains('>') {
        return (content.to_string(), Vec::new());
    }

    let mut words: Vec<LyricsWord> = Vec::new();
    let mut plain = String::new();
    let mut rest = content;

    while let Some(open) = rest.find('<') {
        let before = &rest[..open];
        if !before.is_empty() {
            plain.push_str(before);
            if let Some(last) = words.last_mut() {
                last.text.push_str(before);
            }
        }
        let after_open = &rest[open + 1..];
        let Some(close) = after_open.find('>') else {
            plain.push_str(&rest[open..]);
            break;
        };
        let tag = &after_open[..close];
        if let Some(ms) = parse_timestamp_ms(tag) {
            if let Some(prev) = words.last_mut() {
                prev.end_ms = ms;
            }
            words.push(LyricsWord {
                text: String::new(),
                start_ms: ms,
                end_ms: ms,
                is_background: false,
            });
            rest = &after_open[close + 1..];
        } else {
            plain.push_str(&rest[open..open + 1 + close + 1]);
            rest = &after_open[close + 1..];
        }
    }

    if !rest.is_empty() {
        plain.push_str(rest);
        if let Some(last) = words.last_mut() {
            last.text.push_str(rest);
        }
    }

    let words = words
        .into_iter()
        .filter(|w| !w.text.is_empty())
        .collect::<Vec<_>>();

    (plain.trim().to_string(), words)
}

/// Parses TTML or LRC timestamps (`HH:MM:SS.mmm`, `MM:SS.xx`, `SS.mmm`, `12.3s`, `1234ms`).
pub fn parse_timestamp_ms(raw: &str) -> Option<i64> {
    let s = raw.trim();
    if s.is_empty() {
        return None;
    }
    if let Some(ms_str) = s.strip_suffix("ms") {
        return ms_str.trim().parse::<f64>().ok().map(|v| v.round() as i64);
    }
    if let Some(s_str) = s.strip_suffix('s') {
        return s_str
            .trim()
            .parse::<f64>()
            .ok()
            .map(|v| (v * 1000.0).round() as i64);
    }

    let parts: Vec<&str> = s.split(':').collect();
    match parts.len() {
        1 => parts[0]
            .parse::<f64>()
            .ok()
            .map(|sec| (sec * 1000.0).round() as i64),
        2 => {
            let mins: i64 = parts[0].parse().ok()?;
            let secs: f64 = parts[1].parse().ok()?;
            Some(mins * 60_000 + (secs * 1000.0).round() as i64)
        }
        3 => {
            let hrs: i64 = parts[0].parse().ok()?;
            let mins: i64 = parts[1].parse().ok()?;
            let secs: f64 = parts[2].parse().ok()?;
            Some(hrs * 3_600_000 + mins * 60_000 + (secs * 1000.0).round() as i64)
        }
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_timestamp_formats() {
        assert_eq!(parse_timestamp_ms("01:23.45"), Some(83_450));
        assert_eq!(parse_timestamp_ms("00:01:02.500"), Some(62_500));
        assert_eq!(parse_timestamp_ms("12.34s"), Some(12_340));
        assert_eq!(parse_timestamp_ms("500ms"), Some(500));
        assert_eq!(parse_timestamp_ms("ar:Artist"), None);
    }

    #[test]
    fn test_parse_ttml_syllable_bg_and_romanized() {
        let ttml = r#"
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
          <body>
            <div>
              <p begin="00:00:10.000" end="00:00:13.500" ttm:agent="v1">
                <span begin="00:00:10.000" end="00:00:10.800">Hello </span>
                <span begin="00:00:10.800" end="00:00:12.000">world</span>
                <span ttm:role="x-bg"><span begin="00:00:12.100" end="00:00:13.200">(ooh)</span></span>
                <span ttm:role="x-roman">he-lo wo-rld</span>
              </p>
            </div>
          </body>
        </tt>
        "#;

        let parsed = SpicyLyricsParser::parse_auto(ttml, "spicy-api");
        assert_eq!(parsed.kind, LyricsKind::Syllable);
        assert_eq!(parsed.lines.len(), 1);
        let line = &parsed.lines[0];
        assert_eq!(line.text, "Hello world");
        assert_eq!(line.start_ms, Some(10_000));
        assert_eq!(line.end_ms, Some(13_500));
        assert_eq!(line.alignment.as_deref(), Some("left"));
        assert_eq!(line.words.len(), 2);
        assert_eq!(line.background_words.len(), 1);
        assert_eq!(line.background_words[0].text, "(ooh)");
        assert!(line.background_words[0].is_background);
        assert_eq!(line.romanized.as_deref(), Some("he-lo wo-rld"));
    }

    #[test]
    fn test_parse_enhanced_lrc_and_standard_lrc() {
        let enhanced = "[ti:Test]\n[00:05.00]<00:05.00>Wake <00:05.50>up <00:06.20>now\n[00:10.00]Second line";
        let parsed = SpicyLyricsParser::parse_auto(enhanced, "sidecar");
        assert_eq!(parsed.kind, LyricsKind::Syllable);
        assert_eq!(parsed.lines.len(), 2);
        assert_eq!(parsed.lines[0].text, "Wake up now");
        assert_eq!(parsed.lines[0].end_ms, Some(10_000));
        assert_eq!(parsed.lines[0].words.len(), 3);
        assert_eq!(parsed.lines[0].words[0].start_ms, 5000);
        assert_eq!(parsed.lines[0].words[0].end_ms, 5500);
        assert_eq!(parsed.lines[0].words[2].end_ms, 10_000);

        let standard = "[00:01.00][00:20.00]Chorus line\n[00:05.00]Verse line";
        let parsed_std = SpicyLyricsParser::parse_lrc(standard, "sidecar");
        assert_eq!(parsed_std.kind, LyricsKind::Line);
        assert_eq!(parsed_std.lines.len(), 3);
        assert_eq!(parsed_std.lines[0].start_ms, Some(1000));
        assert_eq!(parsed_std.lines[1].start_ms, Some(5000));
        assert_eq!(parsed_std.lines[2].start_ms, Some(20_000));
    }

    #[test]
    fn test_parse_plain_text_fallback() {
        let plain = "First line\n\nSecond line\n";
        let parsed = SpicyLyricsParser::parse_auto(plain, "embedded");
        assert_eq!(parsed.kind, LyricsKind::Static);
        assert_eq!(parsed.lines.len(), 2);
        assert_eq!(parsed.lines[0].start_ms, None);
    }
}
