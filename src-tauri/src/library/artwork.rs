use image::{imageops::FilterType, DynamicImage, GenericImageView};
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum ArtworkError {
    #[error("Image decode/encode error: {0}")]
    Image(#[from] image::ImageError),
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),
}

pub type ArtworkResult<T> = Result<T, ArtworkError>;

/// Computes a deterministic 64-bit FNV-1a hex digest for cache filenames.
pub fn fnv1a_hex(data: &[u8]) -> String {
    let mut hash: u64 = 0xcbf29ce484222325;
    for &b in data {
        hash ^= b as u64;
        hash = hash.wrapping_mul(0x100000001b3);
    }
    format!("{hash:016x}")
}

/// Saves cover art bytes into `<cache_root>/covers-v1/<hash>.png` (reusing if already cached)
/// and returns `(cached_path, accent_hex_color)`.
pub fn save_cover_to_cache(
    cache_root: &Path,
    image_bytes: &[u8],
) -> ArtworkResult<(PathBuf, String)> {
    let covers_dir = cache_root.join("covers-v1");
    std::fs::create_dir_all(&covers_dir)?;

    let hash = fnv1a_hex(image_bytes);
    let target_path = covers_dir.join(format!("{hash}.png"));

    let img = image::load_from_memory(image_bytes)?;
    let accent = extract_dominant_accent_color(&img);

    if !target_path.exists() {
        let resized = if img.width() > 600 || img.height() > 600 {
            img.resize(600, 600, FilterType::Triangle)
        } else {
            img
        };
        resized.save(&target_path)?;
    }

    Ok((target_path, accent))
}

/// Extracts a vibrant dominant/accent hex color (`#RRGGBB`) from an album cover image
/// for Material 3 album-adaptive theming.
pub fn extract_dominant_accent_color(img: &DynamicImage) -> String {
    let thumb = img.resize_exact(32, 32, FilterType::Triangle);
    let mut buckets: HashMap<(u8, u8, u8), (u64, f32)> = HashMap::new();
    let mut r_sum: u64 = 0;
    let mut g_sum: u64 = 0;
    let mut b_sum: u64 = 0;
    let mut count: u64 = 0;

    for (_, _, px) in thumb.pixels() {
        let [r, g, b, a] = px.0;
        if a < 128 {
            continue;
        }
        r_sum += r as u64;
        g_sum += g as u64;
        b_sum += b as u64;
        count += 1;

        let max = r.max(g).max(b) as f32 / 255.0;
        let min = r.min(g).min(b) as f32 / 255.0;
        let l = (max + min) * 0.5;
        let s = if (max - min).abs() < f32::EPSILON {
            0.0
        } else {
            (max - min) / (1.0 - (2.0 * l - 1.0).abs()).max(0.001)
        };

        // Skip near-black, near-white, and desaturated grays for accent selection
        if l < 0.14 || l > 0.88 || s < 0.18 {
            continue;
        }

        // Quantize to 5-bit per channel for stable clustering
        let qr = r >> 3;
        let qg = g >> 3;
        let qb = b >> 3;
        let weight = 1.0 + s * 2.5;
        let entry = buckets.entry((qr, qg, qb)).or_insert((0, 0.0));
        entry.0 += 1;
        entry.1 += weight;
    }

    if let Some(((qr, qg, qb), _)) = buckets
        .into_iter()
        .max_by(|a, b| a.1 .1.partial_cmp(&b.1 .1).unwrap_or(std::cmp::Ordering::Equal))
    {
        let r = (qr << 3) | 0x04;
        let g = (qg << 3) | 0x04;
        let b = (qb << 3) | 0x04;
        return format!("#{r:02X}{g:02X}{b:02X}");
    }

    if count > 0 {
        let r = (r_sum / count) as u8;
        let g = (g_sum / count) as u8;
        let b = (b_sum / count) as u8;
        return format!("#{r:02X}{g:02X}{b:02X}");
    }

    "#6750A4".to_string()
}

#[cfg(test)]
mod tests {
    use super::*;
    use image::{ImageBuffer, Rgba};

    #[test]
    fn test_extract_dominant_accent_color_vibrant() {
        let img = DynamicImage::ImageRgba8(ImageBuffer::from_pixel(
            32,
            32,
            Rgba([30, 144, 255, 255]),
        ));
        let hex = extract_dominant_accent_color(&img);
        assert!(hex.starts_with('#') && hex.len() == 7);
        // Should be close to #1E90FF (quantized to 5-bit)
        assert_eq!(hex, "#1C94FC");
    }

    #[test]
    fn test_save_cover_to_cache_creates_png() {
        let tmp = std::env::temp_dir().join(format!("umimuko_art_test_{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&tmp);
        std::fs::create_dir_all(&tmp).unwrap();

        let img = DynamicImage::ImageRgba8(ImageBuffer::from_pixel(
            8,
            8,
            Rgba([220, 60, 80, 255]),
        ));
        let mut bytes = Vec::new();
        img.write_to(
            &mut std::io::Cursor::new(&mut bytes),
            image::ImageFormat::Png,
        )
        .unwrap();

        let (path, hex) = save_cover_to_cache(&tmp, &bytes).unwrap();
        assert!(path.exists());
        assert!(path.to_string_lossy().contains("covers-v1"));
        assert!(hex.starts_with('#'));

        let _ = std::fs::remove_dir_all(&tmp);
    }
}
