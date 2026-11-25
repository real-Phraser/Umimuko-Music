use crate::library::artwork::fnv1a_hex;
use crate::library::metadata::read_metadata;
use crate::lyrics::parser::{SpicyLyrics, SpicyLyricsParser};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::RwLock;
use std::time::Duration;

const DEFAULT_SPICY_LYRICS_API: &str = "https://api.spicylyrics.org";

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LyricsQuery {
    pub title: String,
    pub artist: String,
    pub album: Option<String>,
    pub duration_ms: Option<i64>,
    pub track_path: Option<PathBuf>,
    pub embedded_lyrics: Option<String>,
    pub api_key: Option<String>,
}

pub struct SpicyLyricsService {
    cache_dir: PathBuf,
    memory_cache: RwLock<HashMap<String, SpicyLyrics>>,
    http: reqwest::Client,
    spicy_api_base: String,
}

impl SpicyLyricsService {
    pub fn new(cache_root: &Path) -> Self {
        let http = reqwest::Client::builder()
            .timeout(Duration::from_secs(8))
            .user_agent("UmimukoMusic/0.26.1")
            .build()
            .unwrap_or_else(|_| reqwest::Client::new());

        Self {
            cache_dir: cache_root.join("spicy-lyrics-v2"),
            memory_cache: RwLock::new(HashMap::new()),
            http,
            spicy_api_base: DEFAULT_SPICY_LYRICS_API.to_string(),
        }
    }

    pub fn with_api_base(mut self, base_url: impl Into<String>) -> Self {
        self.spicy_api_base = base_url.into().trim_end_matches('/').to_string();
        self
    }

    fn cache_key(query: &LyricsQuery) -> String {
        let raw = format!(
            "{}::{}::{}",
            query.artist.trim().to_ascii_lowercase(),
            query.title.trim().to_ascii_lowercase(),
            query.album.as_deref().unwrap_or("").trim().to_ascii_lowercase()
        );
        fnv1a_hex(raw.as_bytes())
    }

    fn disk_cache_path(&self, key: &str) -> PathBuf {
        self.cache_dir.join(format!("{key}.json"))
    }

    fn read_disk_cache(&self, key: &str) -> Option<SpicyLyrics> {
        let path = self.disk_cache_path(key);
        let bytes = std::fs::read(path).ok()?;
        serde_json::from_slice(&bytes).ok()
    }

    fn write_disk_cache(&self, key: &str, lyrics: &SpicyLyrics) {
        if std::fs::create_dir_all(&self.cache_dir).is_ok() {
            if let Ok(json) = serde_json::to_vec(lyrics) {
                let _ = std::fs::write(self.disk_cache_path(key), json);
            }
        }
    }

    /// Resolves lyrics in priority order:
    /// 1. In-memory cache
    /// 2. Disk cache (`spicy-lyrics-v2`)
    /// 3. Local `.ttml` / `.lrc` sidecar files
    /// 4. Spicy Lyrics API (+ iTunes search track matching)
    /// 5. Embedded audio file lyrics fallback
    pub async fn fetch_lyrics(&self, query: &LyricsQuery) -> Option<SpicyLyrics> {
        let key = Self::cache_key(query);

        if let Ok(guard) = self.memory_cache.read() {
            if let Some(cached) = guard.get(&key) {
                return Some(cached.clone());
            }
        }

        if let Some(disk_cached) = self.read_disk_cache(&key) {
            if let Ok(mut guard) = self.memory_cache.write() {
                guard.insert(key, disk_cached.clone());
            }
            return Some(disk_cached);
        }

        // 3. Sidecar (.ttml / .lrc) fallback
        if let Some(ref track_path) = query.track_path {
            if let Some(sidecar) = Self::load_sidecar_lyrics(track_path) {
                self.store_cache(&key, &sidecar);
                return Some(sidecar);
            }
        }

        // 4. Spicy Lyrics API + iTunes search matching
        if let Some(remote) = self.fetch_from_spicy_api(query).await {
            self.store_cache(&key, &remote);
            return Some(remote);
        }

        // 5. Embedded lyrics fallback
        if let Some(ref embedded) = query.embedded_lyrics {
            if !embedded.trim().is_empty() {
                let parsed = SpicyLyricsParser::parse_auto(embedded, "embedded");
                self.store_cache(&key, &parsed);
                return Some(parsed);
            }
        } else if let Some(ref track_path) = query.track_path {
            if let Ok(meta) = read_metadata(track_path) {
                if let Some(embedded) = meta.embedded_lyrics {
                    let parsed = SpicyLyricsParser::parse_auto(&embedded, "embedded");
                    self.store_cache(&key, &parsed);
                    return Some(parsed);
                }
            }
        }

        None
    }

    fn store_cache(&self, key: &str, lyrics: &SpicyLyrics) {
        if let Ok(mut guard) = self.memory_cache.write() {
            guard.insert(key.to_string(), lyrics.clone());
        }
        self.write_disk_cache(key, lyrics);
    }

    /// Removes any cached lyrics for `query` from memory and disk so the next fetch refreshes.
    pub fn invalidate_cache(&self, query: &LyricsQuery) {
        let key = Self::cache_key(query);
        if let Ok(mut guard) = self.memory_cache.write() {
            guard.remove(&key);
        }
        let path = self.disk_cache_path(&key);
        if path.exists() {
            let _ = std::fs::remove_file(path);
        }
    }

    /// Loads `.ttml` or `.lrc` sidecar file next to `track_path`.
    pub fn load_sidecar_lyrics(track_path: &Path) -> Option<SpicyLyrics> {
        for ext in ["ttml", "lrc"] {
            let sidecar = track_path.with_extension(ext);
            if sidecar.is_file() {
                if let Ok(content) = std::fs::read_to_string(&sidecar) {
                    if !content.trim().is_empty() {
                        return Some(SpicyLyricsParser::parse_auto(&content, &format!("sidecar:{ext}")));
                    }
                }
            }
        }
        None
    }

    async fn fetch_from_spicy_api(&self, query: &LyricsQuery) -> Option<SpicyLyrics> {
        let itunes_id = self.search_itunes_track_id(query).await;
        let url = format!("{}/query", self.spicy_api_base);

        let mut req = self.http.post(&url).json(&serde_json::json!({
            "queries": [{
                "operationName": "getLyrics",
                "variables": {
                    "id": itunes_id,
                    "title": query.title,
                    "artist": query.artist,
                    "album": query.album,
                    "durationMs": query.duration_ms,
                }
            }]
        }));

        if let Some(ref api_key) = query.api_key {
            if !api_key.trim().is_empty() {
                req = req.bearer_auth(api_key.trim());
            }
        }

        let resp = req.send().await.ok()?;
        if !resp.status().is_success() {
            return None;
        }
        let body: serde_json::Value = resp.json().await.ok()?;
        let raw_lyrics = extract_lyrics_payload(&body)?;
        let parsed = SpicyLyricsParser::parse_auto(&raw_lyrics, "spicy-lyrics");
        if parsed.lines.is_empty() {
            None
        } else {
            Some(parsed)
        }
    }

    async fn search_itunes_track_id(&self, query: &LyricsQuery) -> Option<String> {
        let term = format!("{} {}", query.artist, query.title);
        let resp = self
            .http
            .get("https://itunes.apple.com/search")
            .query(&[
                ("term", term.as_str()),
                ("media", "music"),
                ("entity", "song"),
                ("limit", "3"),
            ])
            .send()
            .await
            .ok()?;

        if !resp.status().is_success() {
            return None;
        }
        let data: ItunesSearchResponse = resp.json().await.ok()?;
        let first = data.results.into_iter().next()?;
        Some(first.track_id.to_string())
    }
}

#[derive(Deserialize)]
struct ItunesSearchResponse {
    #[serde(default)]
    results: Vec<ItunesTrackResult>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ItunesTrackResult {
    track_id: u64,
}

fn extract_lyrics_payload(val: &serde_json::Value) -> Option<String> {
    if let Some(s) = val.get("ttml").and_then(|v| v.as_str()) {
        return Some(s.to_string());
    }
    if let Some(s) = val.get("lyrics").and_then(|v| v.as_str()) {
        return Some(s.to_string());
    }
    if let Some(arr) = val.get("queries").and_then(|v| v.as_array()) {
        for item in arr {
            if let Some(res) = item.get("result") {
                if let Some(s) = extract_lyrics_payload(res) {
                    return Some(s);
                }
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_sidecar_and_disk_cache_fallback() {
        let tmp = std::env::temp_dir().join(format!("umimuko_lyr_svc_test_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&tmp);
        std::fs::create_dir_all(&tmp).unwrap();

        let track_path = tmp.join("song.flac");
        let lrc_path = tmp.join("song.lrc");
        std::fs::write(&track_path, b"dummy").unwrap();
        std::fs::write(&lrc_path, "[00:01.00]Hello from sidecar").unwrap();

        let svc = SpicyLyricsService::new(&tmp).with_api_base("http://127.0.0.1:9");
        let query = LyricsQuery {
            title: "Song".into(),
            artist: "Artist".into(),
            album: None,
            duration_ms: None,
            track_path: Some(track_path),
            embedded_lyrics: None,
            api_key: None,
        };

        let first = svc.fetch_lyrics(&query).await.expect("should load sidecar");
        assert_eq!(first.source, "sidecar:lrc");
        assert_eq!(first.lines[0].text, "Hello from sidecar");

        // Remove sidecar to verify memory/disk cache serves it
        std::fs::remove_file(&lrc_path).unwrap();
        let second = svc.fetch_lyrics(&query).await.expect("should hit cache");
        assert_eq!(second.lines[0].text, "Hello from sidecar");

        let _ = std::fs::remove_dir_all(&tmp);
    }
}
