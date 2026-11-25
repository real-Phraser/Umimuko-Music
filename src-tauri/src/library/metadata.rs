use lofty::config::WriteOptions;
use lofty::file::{AudioFile, TaggedFileExt};
use lofty::picture::{MimeType, Picture, PictureType};
use lofty::prelude::{Accessor, ItemKey, TagExt};
use lofty::tag::{Tag, TagType};
use serde::{Deserialize, Serialize};
use std::path::Path;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum MetadataError {
    #[error("Lofty tag error: {0}")]
    Lofty(#[from] lofty::error::LoftyError),
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
    #[error("No writable tag found on file")]
    NoTag,
}

pub type MetadataResult<T> = Result<T, MetadataError>;

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct AudioMetadata {
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub genre: String,
    pub year: Option<i32>,
    pub track_num: Option<i32>,
    pub duration_ms: i64,
    pub embedded_lyrics: Option<String>,
    pub cover_art: Option<Vec<u8>>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct MetadataUpdate {
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub album_artist: Option<String>,
    pub genre: Option<String>,
    pub year: Option<i32>,
    pub track_num: Option<i32>,
    pub embedded_lyrics: Option<String>,
    pub cover_art: Option<Vec<u8>>,
}

/// Reads audio tags and duration from an audio file using `lofty`.
pub fn read_metadata(path: &Path) -> MetadataResult<AudioMetadata> {
    let tagged_file = lofty::read_from_path(path)?;
    let duration_ms = tagged_file.properties().duration().as_millis() as i64;
    let fallback_title = path
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("Unknown Track")
        .to_string();

    let tag = tagged_file
        .primary_tag()
        .or_else(|| tagged_file.first_tag());

    let mut meta = AudioMetadata {
        title: fallback_title,
        artist: "Unknown Artist".to_string(),
        album: "Unknown Album".to_string(),
        album_artist: String::new(),
        genre: String::new(),
        year: None,
        track_num: None,
        duration_ms,
        embedded_lyrics: None,
        cover_art: None,
    };

    if let Some(tag) = tag {
        if let Some(t) = tag.title().filter(|s| !s.trim().is_empty()) {
            meta.title = t.to_string();
        }
        if let Some(a) = tag.artist().filter(|s| !s.trim().is_empty()) {
            meta.artist = a.to_string();
        }
        if let Some(al) = tag.album().filter(|s| !s.trim().is_empty()) {
            meta.album = al.to_string();
        }
        if let Some(aa) = tag.get_string(&ItemKey::AlbumArtist) {
            if !aa.trim().is_empty() {
                meta.album_artist = aa.to_string();
            }
        }
        if let Some(g) = tag.genre() {
            meta.genre = g.to_string();
        }
        meta.year = tag.year().map(|y| y as i32);
        meta.track_num = tag.track().map(|n| n as i32);

        if let Some(lyr) = tag.get_string(&ItemKey::Lyrics) {
            if !lyr.trim().is_empty() {
                meta.embedded_lyrics = Some(lyr.to_string());
            }
        }

        let front_pic = tag
            .pictures()
            .iter()
            .find(|p| p.pic_type() == PictureType::CoverFront)
            .or_else(|| tag.pictures().first());
        if let Some(pic) = front_pic {
            meta.cover_art = Some(pic.data().to_vec());
        }
    }

    Ok(meta)
}

/// Writes updated tags back to the audio file using `lofty`.
pub fn write_metadata(path: &Path, update: &MetadataUpdate) -> MetadataResult<()> {
    let mut tagged_file = lofty::read_from_path(path)?;
    let tag_type = tagged_file.primary_tag_type();

    if tagged_file.primary_tag().is_none() {
        let fallback_type = match tag_type {
            TagType::Id3v2 | TagType::Mp4Ilst | TagType::VorbisComments | TagType::Ape => tag_type,
            _ => TagType::Id3v2,
        };
        tagged_file.insert_tag(Tag::new(fallback_type));
    }

    let tag = tagged_file.primary_tag_mut().ok_or(MetadataError::NoTag)?;

    if let Some(ref title) = update.title {
        tag.set_title(title.clone());
    }
    if let Some(ref artist) = update.artist {
        tag.set_artist(artist.clone());
    }
    if let Some(ref album) = update.album {
        tag.set_album(album.clone());
    }
    if let Some(ref album_artist) = update.album_artist {
        if album_artist.is_empty() {
            tag.remove_key(&ItemKey::AlbumArtist);
        } else {
            tag.insert_text(ItemKey::AlbumArtist, album_artist.clone());
        }
    }
    if let Some(ref genre) = update.genre {
        tag.set_genre(genre.clone());
    }
    if let Some(year) = update.year {
        if year > 0 {
            tag.set_year(year as u32);
        } else {
            tag.remove_year();
        }
    }
    if let Some(track_num) = update.track_num {
        if track_num > 0 {
            tag.set_track(track_num as u32);
        } else {
            tag.remove_track();
        }
    }
    if let Some(ref lyrics) = update.embedded_lyrics {
        if lyrics.is_empty() {
            tag.remove_key(&ItemKey::Lyrics);
        } else {
            tag.insert_text(ItemKey::Lyrics, lyrics.clone());
        }
    }
    if let Some(ref cover_bytes) = update.cover_art {
        tag.remove_picture_type(PictureType::CoverFront);
        if !cover_bytes.is_empty() {
            let mime = detect_mime_type(cover_bytes);
            let pic = Picture::new_unchecked(
                PictureType::CoverFront,
                Some(mime),
                None,
                cover_bytes.clone(),
            );
            tag.push_picture(pic);
        }
    }

    tag.save_to_path(path, WriteOptions::default())?;
    Ok(())
}

fn detect_mime_type(bytes: &[u8]) -> MimeType {
    if bytes.starts_with(&[0x89, 0x50, 0x4E, 0x47]) {
        MimeType::Png
    } else if bytes.starts_with(&[0xFF, 0xD8, 0xFF]) {
        MimeType::Jpeg
    } else {
        MimeType::Png
    }
}
