use crate::library::artwork::fnv1a_hex;
use crate::library::metadata::read_metadata;
use crate::lyrics::parser::{SpicyLyrics, SpicyLyricsParser};
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::RwLock;
use std::time::Duration;

const DEFAULT_SPICY_LYRICS_API: &str = "https://api.spicylyrics.org";
const DEFAULT_ITUNES_API: &str = "https://itunes.apple.com";
const USER_AGENT: &str = "UmimukoMusic/0.26.1 ( https://github.com/real-Phraser )";
const CACHE_SUBDIR: &str = "spicy-lyrics-v2";
const ITUNES_SEARCH_LIMIT: usize = 25;
const ITUNES_ARTIST_SEARCH_LIMIT: usize = 5;
const ITUNES_LOOKUP_LIMIT: usize = 200;
const MIN_ARTIST_LOOKUP_SCORE: i32 = 30;
const MAX_DURATION_DIFF_MS: i64 = 15_000;
const MIN_MATCH_SCORE: i32 = 40;
const ARTIST_STOPWORDS: &[&str] = &["the", "a", "an", "and", "of"];

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
    itunes_api_base: String,
}

impl SpicyLyricsService {
    pub fn new(cache_root: &Path) -> Self {
        let http = reqwest::Client::builder()
            .timeout(Duration::from_secs(8))
            .connect_timeout(Duration::from_secs(6))
            .user_agent(USER_AGENT)
            .build()
            .unwrap_or_else(|_| reqwest::Client::new());

        Self {
            cache_dir: cache_root.join(CACHE_SUBDIR),
            memory_cache: RwLock::new(HashMap::new()),
            http,
            spicy_api_base: DEFAULT_SPICY_LYRICS_API.to_string(),
            itunes_api_base: DEFAULT_ITUNES_API.to_string(),
        }
    }

    pub fn with_api_base(mut self, base_url: impl Into<String>) -> Self {
        self.spicy_api_base = base_url.into().trim_end_matches('/').to_string();
        self
    }

    pub fn with_itunes_base(mut self, base_url: impl Into<String>) -> Self {
        self.itunes_api_base = base_url.into().trim_end_matches('/').to_string();
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

    /// Resolves lyrics in priority order (matching SpicyLyricsService.kt):
    /// 1. In-memory cache
    /// 2. Disk cache (`spicy-lyrics-v2`)
    /// 3. Spicy Lyrics API (`GET /v1/lyrics/{trackId}`, if `api_key` is present)
    /// 4. Local `.ttml` / `.lrc` sidecar files
    /// 5. Embedded audio file lyrics fallback
    pub async fn fetch_lyrics(&self, query: &LyricsQuery) -> Option<SpicyLyrics> {
        let key = Self::cache_key(query);

        if let Ok(guard) = self.memory_cache.read() {
            if let Some(cached) = guard.get(&key) {
                return Some(cached.clone());
            }
        }

        if let Some(disk_cached) = self.read_disk_cache(&key) {
            if !disk_cached.lines.is_empty() {
                if let Ok(mut guard) = self.memory_cache.write() {
                    guard.insert(key, disk_cached.clone());
                }
                return Some(disk_cached);
            }
        }

        // 3. Spicy Lyrics API + iTunes search matching (only when API key is configured)
        if let Some(ref api_key) = query.api_key {
            let trimmed_key = api_key.trim();
            if !trimmed_key.is_empty() {
                if let Some(remote) = self.fetch_from_spicy_api(query, trimmed_key).await {
                    if !remote.lines.is_empty() {
                        self.store_cache(&key, &remote);
                        return Some(remote);
                    }
                }
            }
        }

        // 4. Sidecar (.ttml / .lrc) fallback
        if let Some(ref track_path) = query.track_path {
            if let Some(sidecar) = Self::load_sidecar_lyrics(track_path) {
                if !sidecar.lines.is_empty() {
                    self.store_cache(&key, &sidecar);
                    return Some(sidecar);
                }
            }
        }

        // 5. Embedded lyrics fallback
        if let Some(ref embedded) = query.embedded_lyrics {
            if !embedded.trim().is_empty() {
                let parsed = SpicyLyricsParser::parse_auto(embedded, "embedded");
                if !parsed.lines.is_empty() {
                    self.store_cache(&key, &parsed);
                    return Some(parsed);
                }
            }
        } else if let Some(ref track_path) = query.track_path {
            if let Ok(meta) = read_metadata(track_path) {
                if let Some(embedded) = meta.embedded_lyrics {
                    let parsed = SpicyLyricsParser::parse_auto(&embedded, "embedded");
                    if !parsed.lines.is_empty() {
                        self.store_cache(&key, &parsed);
                        return Some(parsed);
                    }
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

    /// Loads `.ttml` or `.lrc` sidecar file next to `track_path` (or inside `lyrics/`).
    pub fn load_sidecar_lyrics(track_path: &Path) -> Option<SpicyLyrics> {
        let parent = track_path.parent()?;
        let stem = track_path.file_stem()?.to_string_lossy();
        let candidates = [
            (parent.join(format!("{stem}.ttml")), "sidecar:ttml"),
            (parent.join("lyrics").join(format!("{stem}.ttml")), "sidecar:ttml"),
            (parent.join(format!("{stem}.lrc")), "sidecar:lrc"),
            (parent.join("lyrics").join(format!("{stem}.lrc")), "sidecar:lrc"),
        ];

        for (sidecar, source) in candidates {
            if sidecar.is_file() {
                if let Ok(content) = std::fs::read_to_string(&sidecar) {
                    if !content.trim().is_empty() {
                        let parsed = SpicyLyricsParser::parse_auto(&content, source);
                        if !parsed.lines.is_empty() {
                            return Some(parsed);
                        }
                    }
                }
            }
        }
        None
    }

    async fn fetch_from_spicy_api(&self, query: &LyricsQuery, bearer_token: &str) -> Option<SpicyLyrics> {
        let track_id = self
            .resolve_itunes_track_id(
                &query.artist,
                &query.title,
                query.duration_ms.unwrap_or(0),
                query.album.as_deref().unwrap_or(""),
            )
            .await?;

        let endpoint = format!("{}/v1/lyrics/{track_id}", self.spicy_api_base);
        let resp = self
            .http
            .get(&endpoint)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .bearer_auth(bearer_token)
            .timeout(Duration::from_secs(8))
            .send()
            .await
            .ok()?;

        if !resp.status().is_success() {
            return None;
        }
        let body = resp.text().await.ok()?;
        let parsed = SpicyLyricsParser::parse_auto(&body, "SpicyLyrics");
        if parsed.lines.is_empty() {
            None
        } else {
            Some(parsed)
        }
    }

    pub async fn resolve_itunes_track_id(
        &self,
        artist: &str,
        title: &str,
        duration_ms: i64,
        album: &str,
    ) -> Option<u64> {
        let clean_title = title.trim();
        if clean_title.is_empty() {
            return None;
        }

        let primary_artist = strip_featured_suffix(artist.trim());
        let stripped_title = strip_featured_suffix(clean_title);
        let primary_split_title = strip_featured_suffix(
            clean_title.split('/').next().unwrap_or("").trim(),
        );

        let mut candidate_urls: Vec<String> = Vec::new();
        let mut push_unique = |url: String| {
            if !candidate_urls.contains(&url) {
                candidate_urls.push(url);
            }
        };

        let primary_query = [primary_artist.as_str(), clean_title]
            .into_iter()
            .filter(|s| !s.is_empty())
            .collect::<Vec<_>>()
            .join(" ");
        if !primary_query.trim().is_empty() {
            push_unique(self.build_itunes_search_url(&primary_query, "song", None, ITUNES_SEARCH_LIMIT));
        }

        if !primary_split_title.is_empty() && !primary_split_title.eq_ignore_ascii_case(clean_title) {
            let split_query = [primary_artist.as_str(), primary_split_title.as_str()]
                .into_iter()
                .filter(|s| !s.is_empty())
                .collect::<Vec<_>>()
                .join(" ");
            push_unique(self.build_itunes_search_url(&split_query, "song", None, ITUNES_SEARCH_LIMIT));
        }

        push_unique(self.build_itunes_search_url(
            clean_title,
            "song",
            Some("songTerm"),
            ITUNES_SEARCH_LIMIT,
        ));

        if !stripped_title.is_empty() && !stripped_title.eq_ignore_ascii_case(clean_title) {
            push_unique(self.build_itunes_search_url(
                &stripped_title,
                "song",
                Some("songTerm"),
                ITUNES_SEARCH_LIMIT,
            ));
        }

        if !primary_split_title.is_empty()
            && !primary_split_title.eq_ignore_ascii_case(clean_title)
            && !primary_split_title.eq_ignore_ascii_case(&stripped_title)
        {
            push_unique(self.build_itunes_search_url(
                &primary_split_title,
                "song",
                Some("songTerm"),
                ITUNES_SEARCH_LIMIT,
            ));
        }

        for url in candidate_urls {
            if let Some(id) = self.query_itunes_search(&url, artist, title, duration_ms).await {
                return Some(id);
            }
        }

        if !primary_artist.is_empty() {
            if let Some(id) = self
                .lookup_itunes_track_by_artist(&primary_artist, artist, title, duration_ms)
                .await
            {
                return Some(id);
            }
        }

        if !album.trim().is_empty() {
            if let Some(id) = self
                .lookup_itunes_track_by_album(&primary_artist, album, artist, title, duration_ms)
                .await
            {
                return Some(id);
            }
        }

        None
    }

    fn build_itunes_search_url(
        &self,
        term: &str,
        entity: &str,
        attribute: Option<&str>,
        limit: usize,
    ) -> String {
        let encoded = url_encode(term);
        let attr_param = attribute
            .map(|a| format!("&attribute={a}"))
            .unwrap_or_default();
        format!(
            "{}/search?term={encoded}&entity={entity}{attr_param}&limit={limit}",
            self.itunes_api_base
        )
    }

    fn build_itunes_lookup_url(&self, id: u64, entity: &str, limit: usize) -> String {
        format!(
            "{}/lookup?id={id}&entity={entity}&limit={limit}",
            self.itunes_api_base
        )
    }

    async fn fetch_itunes_json(&self, url: &str) -> Option<String> {
        let resp = self
            .http
            .get(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .timeout(Duration::from_secs(6))
            .send()
            .await
            .ok()?;
        if resp.status().is_success() {
            resp.text().await.ok()
        } else {
            None
        }
    }

    async fn query_itunes_search(
        &self,
        url: &str,
        artist: &str,
        title: &str,
        duration_ms: i64,
    ) -> Option<u64> {
        let body = self.fetch_itunes_json(url).await?;
        pick_best_itunes_track_id(&body, artist, title, duration_ms)
    }

    async fn lookup_itunes_track_by_artist(
        &self,
        primary_artist: &str,
        artist: &str,
        title: &str,
        duration_ms: i64,
    ) -> Option<u64> {
        let artist_search_url = self.build_itunes_search_url(
            primary_artist,
            "musicArtist",
            None,
            ITUNES_ARTIST_SEARCH_LIMIT,
        );
        let artist_json = self.fetch_itunes_json(&artist_search_url).await?;
        let artist_ids = extract_matching_itunes_artist_ids(&artist_json, primary_artist);
        for artist_id in artist_ids {
            let lookup_url = self.build_itunes_lookup_url(artist_id, "song", ITUNES_LOOKUP_LIMIT);
            if let Some(id) = self
                .query_itunes_search(&lookup_url, artist, title, duration_ms)
                .await
            {
                return Some(id);
            }
        }
        None
    }

    async fn lookup_itunes_track_by_album(
        &self,
        primary_artist: &str,
        album: &str,
        artist: &str,
        title: &str,
        duration_ms: i64,
    ) -> Option<u64> {
        let clean_album = album.trim();
        if clean_album.is_empty() {
            return None;
        }
        let album_query = [primary_artist, clean_album]
            .into_iter()
            .filter(|s| !s.is_empty())
            .collect::<Vec<_>>()
            .join(" ");
        let album_search_url = self.build_itunes_search_url(
            &album_query,
            "album",
            None,
            ITUNES_ARTIST_SEARCH_LIMIT,
        );
        let album_json = self.fetch_itunes_json(&album_search_url).await?;
        let collection_ids = extract_matching_itunes_album_ids(&album_json, primary_artist, clean_album);
        for collection_id in collection_ids {
            let lookup_url = self.build_itunes_lookup_url(collection_id, "song", ITUNES_LOOKUP_LIMIT);
            if let Some(id) = self
                .query_itunes_search(&lookup_url, artist, title, duration_ms)
                .await
            {
                return Some(id);
            }
        }
        None
    }
}

pub fn extract_matching_itunes_artist_ids(itunes_json: &str, artist: &str) -> Vec<u64> {
    let Ok(root) = serde_json::from_str::<serde_json::Value>(itunes_json) else {
        return Vec::new();
    };
    let Some(results) = root.get("results").and_then(|v| v.as_array()) else {
        return Vec::new();
    };
    if results.is_empty() {
        return Vec::new();
    }

    let raw_artist = artist.trim().to_lowercase();
    let norm_artist = normalize_for_match(artist);
    if norm_artist.is_empty() {
        return Vec::new();
    }
    let artist_tokens = significant_tokens(&norm_artist);

    let mut matches: Vec<(u64, i32)> = Vec::new();
    for item in results {
        let Some(map) = item.as_object() else { continue };
        let Some(artist_id) = map.get("artistId").and_then(|v| v.as_u64()).filter(|&id| id > 0) else {
            continue;
        };
        let raw_artist_name = map
            .get("artistName")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .trim()
            .to_lowercase();
        let norm_candidate = normalize_for_match(&raw_artist_name);
        let candidate_tokens = significant_tokens(&norm_candidate);
        if let Some(score) = score_artist_match(
            &raw_artist,
            &norm_artist,
            &artist_tokens,
            &raw_artist_name,
            &norm_candidate,
            &candidate_tokens,
        ) {
            if score >= MIN_ARTIST_LOOKUP_SCORE {
                matches.push((artist_id, score));
            }
        }
    }

    matches.sort_by(|a, b| b.1.cmp(&a.1));
    dedup_u64(matches.into_iter().map(|(id, _)| id))
}

pub fn extract_matching_itunes_album_ids(
    itunes_json: &str,
    artist: &str,
    album: &str,
) -> Vec<u64> {
    let Ok(root) = serde_json::from_str::<serde_json::Value>(itunes_json) else {
        return Vec::new();
    };
    let Some(results) = root.get("results").and_then(|v| v.as_array()) else {
        return Vec::new();
    };
    if results.is_empty() {
        return Vec::new();
    }

    let raw_album = album.trim().to_lowercase();
    let norm_album = normalize_for_match(album);
    if norm_album.is_empty() {
        return Vec::new();
    }
    let album_halves = split_title_halves(album);
    let album_tokens: HashSet<String> = tokenize(&norm_album).into_iter().collect();

    let raw_artist = artist.trim().to_lowercase();
    let norm_artist = normalize_for_match(artist);
    let artist_tokens = significant_tokens(&norm_artist);

    let mut matches: Vec<(u64, i32)> = Vec::new();
    for item in results {
        let Some(map) = item.as_object() else { continue };
        let Some(collection_id) = map
            .get("collectionId")
            .and_then(|v| v.as_u64())
            .filter(|&id| id > 0)
        else {
            continue;
        };
        let raw_collection_name = map
            .get("collectionName")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .trim()
            .to_lowercase();
        let norm_collection = normalize_for_match(&raw_collection_name);
        let collection_halves = split_title_halves(&raw_collection_name);
        let collection_tokens: HashSet<String> = tokenize(&norm_collection).into_iter().collect();

        let Some(album_score) = score_title_match(
            &raw_album,
            &norm_album,
            &album_halves,
            &album_tokens,
            &raw_collection_name,
            &norm_collection,
            &collection_halves,
            &collection_tokens,
        ) else {
            continue;
        };

        let artist_score = if !norm_artist.is_empty() {
            let raw_artist_name = map
                .get("artistName")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .trim()
                .to_lowercase();
            let norm_candidate = normalize_for_match(&raw_artist_name);
            let candidate_tokens = significant_tokens(&norm_candidate);
            let Some(s) = score_artist_match(
                &raw_artist,
                &norm_artist,
                &artist_tokens,
                &raw_artist_name,
                &norm_candidate,
                &candidate_tokens,
            ) else {
                continue;
            };
            s
        } else {
            0
        };

        matches.push((collection_id, album_score + artist_score));
    }

    matches.sort_by(|a, b| b.1.cmp(&a.1));
    dedup_u64(matches.into_iter().map(|(id, _)| id))
}

pub fn pick_best_itunes_track_id(
    itunes_json: &str,
    artist: &str,
    title: &str,
    duration_ms: i64,
) -> Option<u64> {
    let root = serde_json::from_str::<serde_json::Value>(itunes_json).ok()?;
    let results = root.get("results").and_then(|v| v.as_array())?;
    if results.is_empty() {
        return None;
    }

    let raw_title = title.trim().to_lowercase();
    let norm_title = normalize_for_match(title);
    if norm_title.is_empty() {
        return None;
    }
    let title_halves = split_title_halves(title);
    let title_tokens: HashSet<String> = tokenize(&norm_title).into_iter().collect();

    let raw_artist = artist.trim().to_lowercase();
    let norm_artist = normalize_for_match(artist);
    let artist_tokens = significant_tokens(&norm_artist);

    let mut best_id: Option<u64> = None;
    let mut best_score = MIN_MATCH_SCORE - 1;

    for item in results {
        let Some(map) = item.as_object() else { continue };
        let wrapper_type = map
            .get("wrapperType")
            .and_then(|v| v.as_str())
            .map(|s| s.to_ascii_lowercase());
        if matches!(wrapper_type.as_deref(), Some("artist") | Some("collection")) {
            continue;
        }
        let Some(track_id) = map.get("trackId").and_then(|v| v.as_u64()).filter(|&id| id > 0) else {
            continue;
        };

        let raw_track_name = map
            .get("trackName")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .trim()
            .to_lowercase();
        let raw_artist_name = map
            .get("artistName")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .trim()
            .to_lowercase();
        let track_time_ms = map
            .get("trackTimeMillis")
            .and_then(|v| v.as_i64())
            .unwrap_or(0);

        if duration_ms > 0 && track_time_ms > 0 && (duration_ms - track_time_ms).abs() > MAX_DURATION_DIFF_MS {
            continue;
        }

        let norm_track = normalize_for_match(&raw_track_name);
        let track_halves = split_title_halves(&raw_track_name);
        let track_tokens: HashSet<String> = tokenize(&norm_track).into_iter().collect();

        let Some(title_score) = score_title_match(
            &raw_title,
            &norm_title,
            &title_halves,
            &title_tokens,
            &raw_track_name,
            &norm_track,
            &track_halves,
            &track_tokens,
        ) else {
            continue;
        };

        let artist_score = if !norm_artist.is_empty() {
            let norm_track_artist = normalize_for_match(&raw_artist_name);
            let track_artist_tokens = significant_tokens(&norm_track_artist);
            let Some(s) = score_artist_match(
                &raw_artist,
                &norm_artist,
                &artist_tokens,
                &raw_artist_name,
                &norm_track_artist,
                &track_artist_tokens,
            ) else {
                continue;
            };
            s
        } else {
            0
        };

        let mut score = title_score + artist_score;
        if duration_ms > 0 && track_time_ms > 0 {
            let diff = (duration_ms - track_time_ms).abs();
            score += if diff <= 2_000 {
                30
            } else if diff <= 5_000 {
                15
            } else if diff <= 10_000 {
                5
            } else {
                -5
            };
        }

        if score > best_score {
            best_score = score;
            best_id = Some(track_id);
        }
    }

    best_id
}

#[allow(clippy::too_many_arguments)]
fn score_title_match(
    raw_title: &str,
    norm_title: &str,
    title_halves: &[String],
    title_tokens: &HashSet<String>,
    raw_track: &str,
    norm_track: &str,
    track_halves: &[String],
    track_tokens: &HashSet<String>,
) -> Option<i32> {
    if norm_track.is_empty() {
        return None;
    }
    if raw_track == raw_title {
        return Some(60);
    }
    if norm_track == norm_title {
        return Some(50);
    }
    if track_halves.iter().any(|h| h == norm_title) || title_halves.iter().any(|h| h == norm_track) {
        return Some(45);
    }
    if title_tokens.is_empty() || track_tokens.is_empty() {
        return None;
    }
    let common = title_tokens.intersection(track_tokens).count();
    if common == 0 {
        return None;
    }
    if common == title_tokens.len() || common == track_tokens.len() {
        return Some(30);
    }
    let max_tokens = title_tokens.len().max(track_tokens.len());
    if common * 2 >= max_tokens {
        Some(20)
    } else {
        None
    }
}

fn score_artist_match(
    raw_artist: &str,
    norm_artist: &str,
    artist_tokens: &HashSet<String>,
    raw_track_artist: &str,
    norm_track_artist: &str,
    track_artist_tokens: &HashSet<String>,
) -> Option<i32> {
    if norm_track_artist.is_empty() {
        return None;
    }
    if raw_track_artist == raw_artist {
        return Some(50);
    }
    if norm_track_artist == norm_artist {
        return Some(45);
    }
    if norm_track_artist.chars().count() >= 2
        && norm_artist.chars().count() >= 2
        && (norm_track_artist.contains(norm_artist) || norm_artist.contains(norm_track_artist))
    {
        return Some(30);
    }
    if artist_tokens.intersection(track_artist_tokens).next().is_some() {
        Some(20)
    } else {
        None
    }
}

/// Strips `(feat. ...)`, `[ft. ...]`, and trailing ` feat. ...` / ` ft. ...` / ` featuring ...`.
fn strip_featured_suffix(text: &str) -> String {
    let mut s = text.to_string();
    // Remove parenthesized/bracketed feat/ft/featuring
    for (open, close) in [('(', ')'), ('[', ']')] {
        loop {
            let lower = s.to_ascii_lowercase();
            let mut removed = false;
            let mut search_from = 0;
            while let Some(rel_open) = lower[search_from..].find(open) {
                let abs_open = search_from + rel_open;
                if let Some(rel_close) = lower[abs_open + 1..].find(close) {
                    let abs_close = abs_open + 1 + rel_close;
                    let inner = lower[abs_open + 1..abs_close].trim_start();
                    if is_feat_prefix(inner) {
                        let cut_start = s[..abs_open].trim_end().len();
                        let after = &s[abs_close + 1..];
                        s = format!("{}{}", &s[..cut_start], after);
                        removed = true;
                        break;
                    }
                    search_from = abs_close + 1;
                } else {
                    break;
                }
            }
            if !removed {
                break;
            }
        }
    }

    // Remove trailing ` feat. ...`, ` ft. ...`, ` featuring ...`
    let lower = s.to_ascii_lowercase();
    let bytes = lower.as_bytes();
    for kw in ["featuring", "feat.", "feat", "ft.", "ft"] {
        let mut idx = 0;
        while let Some(pos) = lower[idx..].find(kw) {
            let abs = idx + pos;
            let before_ws = abs > 0 && bytes[abs - 1].is_ascii_whitespace();
            let after_idx = abs + kw.len();
            let after_ws = after_idx < bytes.len() && bytes[after_idx].is_ascii_whitespace();
            if before_ws && after_ws {
                s = s[..abs].trim_end().to_string();
                return s.trim().to_string();
            }
            idx = abs + 1;
        }
    }

    s.trim().to_string()
}

fn is_feat_prefix(lower_trimmed: &str) -> bool {
    for kw in ["featuring", "feat.", "feat", "ft.", "ft"] {
        if let Some(rest) = lower_trimmed.strip_prefix(kw) {
            if rest.is_empty() || rest.starts_with(char::is_whitespace) {
                return true;
            }
        }
    }
    false
}

fn normalize_for_match(text: &str) -> String {
    let stripped = strip_featured_suffix(text).to_lowercase();
    let mut out = String::with_capacity(stripped.len());
    let mut prev_space = true;
    for ch in stripped.chars() {
        if ch.is_alphanumeric() {
            out.push(ch);
            prev_space = false;
        } else if !prev_space {
            out.push(' ');
            prev_space = true;
        }
    }
    out.trim().to_string()
}

fn split_title_halves(text: &str) -> Vec<String> {
    if !text.contains('/') {
        return Vec::new();
    }
    text.split('/')
        .map(normalize_for_match)
        .filter(|s| !s.is_empty())
        .collect()
}

fn tokenize(normalized: &str) -> Vec<String> {
    if normalized.is_empty() {
        return Vec::new();
    }
    normalized
        .split_whitespace()
        .filter(|s| !s.is_empty())
        .map(|s| s.to_string())
        .collect()
}

fn significant_tokens(normalized: &str) -> HashSet<String> {
    let all = tokenize(normalized);
    let filtered: Vec<String> = all
        .iter()
        .filter(|t| !ARTIST_STOPWORDS.contains(&t.as_str()))
        .cloned()
        .collect();
    if filtered.is_empty() {
        all.into_iter().collect()
    } else {
        filtered.into_iter().collect()
    }
}

fn dedup_u64<I: IntoIterator<Item = u64>>(iter: I) -> Vec<u64> {
    let mut seen = HashSet::new();
    let mut out = Vec::new();
    for id in iter {
        if seen.insert(id) {
            out.push(id);
        }
    }
    out
}

fn url_encode(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    for byte in input.as_bytes() {
        match *byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'*' => {
                out.push(*byte as char);
            }
            b' ' => out.push('+'),
            _ => {
                out.push('%');
                out.push_str(&format!("{:02X}", byte));
            }
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::lyrics::parser::LyricsKind;
    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    use tokio::net::TcpListener;

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

    #[test]
    fn test_itunes_matching_and_scoring() {
        assert_eq!(strip_featured_suffix("Song (feat. Guest)"), "Song");
        assert_eq!(strip_featured_suffix("Artist ft. Someone"), "Artist");

        let itunes_json = r#"{
            "results": [
                {
                    "wrapperType": "track",
                    "trackId": 111,
                    "trackName": "Wrong Song",
                    "artistName": "Test Artist",
                    "trackTimeMillis": 200000
                },
                {
                    "wrapperType": "track",
                    "trackId": 222,
                    "trackName": "Target Track (feat. Guest)",
                    "artistName": "The Test Artist",
                    "trackTimeMillis": 181000
                },
                {
                    "wrapperType": "track",
                    "trackId": 333,
                    "trackName": "Target Track",
                    "artistName": "Test Artist",
                    "trackTimeMillis": 250000
                }
            ]
        }"#;

        // 333 is filtered out by MAX_DURATION_DIFF_MS (250s vs 180s), 222 matches!
        let picked = pick_best_itunes_track_id(itunes_json, "Test Artist", "Target Track", 180_000);
        assert_eq!(picked, Some(222));

        let artist_json = r#"{
            "results": [
                { "artistId": 10, "artistName": "Other Band" },
                { "artistId": 20, "artistName": "Test Artist" }
            ]
        }"#;
        assert_eq!(extract_matching_itunes_artist_ids(artist_json, "Test Artist"), vec![20]);

        let album_json = r#"{
            "results": [
                { "collectionId": 500, "collectionName": "Greatest Hits", "artistName": "Test Artist" },
                { "collectionId": 501, "collectionName": "Unrelated", "artistName": "Other" }
            ]
        }"#;
        assert_eq!(
            extract_matching_itunes_album_ids(album_json, "Test Artist", "Greatest Hits"),
            vec![500]
        );
    }

    #[tokio::test]
    async fn test_v1_lyrics_endpoint_and_headers() {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();

        let server_task = tokio::spawn(async move {
            for _ in 0..2 {
                let (mut socket, _) = listener.accept().await.unwrap();
                let mut buf = vec![0u8; 4096];
                let n = socket.read(&mut buf).await.unwrap();
                let req = String::from_utf8_lossy(&buf[..n]).to_string();

                let body = if req.starts_with("GET /search?") {
                    r#"{"results":[{"wrapperType":"track","trackId":987654,"trackName":"My Song","artistName":"My Artist","trackTimeMillis":180000}]}"#
                } else if req.starts_with("GET /v1/lyrics/987654 ") {
                    assert!(
                        req.to_ascii_lowercase().contains("authorization: bearer sl_pk_test123"),
                        "missing bearer auth in request: {req}"
                    );
                    assert!(
                        req.contains("UmimukoMusic/0.26.1 ( https://github.com/real-Phraser )"),
                        "missing exact User-Agent in request: {req}"
                    );
                    r#"{"source":"AppleMusic","Body":{"Type":"Syllable","StartTime":1.0,"EndTime":3.0,"Content":[{"Type":"Vocal","Lead":{"StartTime":1.0,"EndTime":3.0,"Syllables":[{"Text":"Hello","StartTime":1.0,"EndTime":2.0,"IsPartOfWord":false},{"Text":"world","StartTime":2.0,"EndTime":3.0,"IsPartOfWord":false}]}}]}}"#
                } else {
                    panic!("Unexpected request: {req}");
                };

                let resp = format!(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                    body.len(),
                    body
                );
                socket.write_all(resp.as_bytes()).await.unwrap();
            }
        });

        let tmp = std::env::temp_dir().join(format!("umimuko_lyr_v1_test_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&tmp);
        let base = format!("http://{addr}");
        let svc = SpicyLyricsService::new(&tmp)
            .with_api_base(&base)
            .with_itunes_base(&base);

        let query = LyricsQuery {
            title: "My Song".into(),
            artist: "My Artist".into(),
            album: None,
            duration_ms: Some(180_000),
            track_path: None,
            embedded_lyrics: None,
            api_key: Some("sl_pk_test123".into()),
        };

        let lyrics = svc.fetch_lyrics(&query).await.expect("should fetch v1 lyrics");
        assert_eq!(lyrics.kind, LyricsKind::Syllable);
        assert_eq!(lyrics.source, "AppleMusic");
        assert_eq!(lyrics.lines.len(), 1);
        assert_eq!(lyrics.lines[0].text, "Hello world");
        assert_eq!(lyrics.lines[0].words.len(), 2);

        server_task.await.unwrap();
        let _ = std::fs::remove_dir_all(&tmp);
    }
}
