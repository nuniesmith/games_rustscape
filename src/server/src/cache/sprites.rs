//! Sprite extraction and export module
//!
//! This module handles reading sprite data from the game cache and
//! exporting them as PNG images for use in the web client.
//!
//! ## Sprite Format
//!
//! RuneScape sprites in the cache are stored as indexed color images:
//! - Header with dimensions and metadata
//! - Color palette (RGB values)
//! - Pixel data as palette indices
//!
//! ## Cache Indices
//!
//! - Index 8: UI Sprites (buttons, icons, interface elements)
//! - Index 32: Textures (ground textures, object textures)
//! - Index 34: Additional sprites (varies by revision)

use std::fs::{self, File};
use std::io::{Cursor, Write};
use std::path::Path;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;

use rayon::prelude::*;
use tracing::{trace, warn};

use crate::error::{CacheError, Result, RustscapeError};

/// Sprite index IDs
pub const SPRITE_INDEX: u8 = 8;
pub const TEXTURE_INDEX: u8 = 32;
pub const EXTRA_SPRITE_INDEX: u8 = 34;

/// A decoded sprite image
#[derive(Debug, Clone)]
pub struct Sprite {
    /// Sprite ID (archive ID in the cache)
    pub id: u32,
    /// Frame index within the sprite archive
    pub frame: u32,
    /// Image width in pixels
    pub width: u32,
    /// Image height in pixels
    pub height: u32,
    /// X offset for positioning
    pub offset_x: i32,
    /// Y offset for positioning
    pub offset_y: i32,
    /// RGBA pixel data (width * height * 4 bytes)
    pub pixels: Vec<u8>,
}

impl Sprite {
    /// Create an empty sprite
    pub fn empty(id: u32, frame: u32) -> Self {
        Self {
            id,
            frame,
            width: 0,
            height: 0,
            offset_x: 0,
            offset_y: 0,
            pixels: Vec::new(),
        }
    }

    /// Check if the sprite has valid image data
    pub fn is_valid(&self) -> bool {
        self.width > 0 && self.height > 0 && !self.pixels.is_empty()
    }

    /// Export the sprite as a PNG file
    pub fn export_png(&self, output_path: &Path) -> Result<()> {
        if !self.is_valid() {
            return Err(RustscapeError::Cache(CacheError::InvalidData(
                "Sprite has no valid image data".to_string(),
            )));
        }

        // Create parent directories if needed
        if let Some(parent) = output_path.parent() {
            fs::create_dir_all(parent).map_err(|e| {
                RustscapeError::Cache(CacheError::Io(format!("Failed to create directory: {}", e)))
            })?;
        }

        // Encode as PNG
        let png_data = self.encode_png()?;

        // Write to file
        let mut file = File::create(output_path).map_err(|e| {
            RustscapeError::Cache(CacheError::Io(format!("Failed to create file: {}", e)))
        })?;

        file.write_all(&png_data).map_err(|e| {
            RustscapeError::Cache(CacheError::Io(format!("Failed to write file: {}", e)))
        })?;

        Ok(())
    }

    /// Encode the sprite as PNG data
    pub fn encode_png(&self) -> Result<Vec<u8>> {
        if !self.is_valid() {
            return Err(RustscapeError::Cache(CacheError::InvalidData(
                "Sprite has no valid image data".to_string(),
            )));
        }

        // Use a simple PNG encoder
        let mut output = Vec::new();

        // PNG signature
        output.extend_from_slice(&[0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);

        // IHDR chunk
        let ihdr = self.create_ihdr_chunk();
        output.extend_from_slice(&ihdr);

        // IDAT chunk(s)
        let idat = self.create_idat_chunk()?;
        output.extend_from_slice(&idat);

        // IEND chunk
        let iend = create_iend_chunk();
        output.extend_from_slice(&iend);

        Ok(output)
    }

    /// Create PNG IHDR chunk
    fn create_ihdr_chunk(&self) -> Vec<u8> {
        let mut data = Vec::with_capacity(13);

        // Width (4 bytes)
        data.extend_from_slice(&(self.width as u32).to_be_bytes());
        // Height (4 bytes)
        data.extend_from_slice(&(self.height as u32).to_be_bytes());
        // Bit depth (8)
        data.push(8);
        // Color type (6 = RGBA)
        data.push(6);
        // Compression method (0 = deflate)
        data.push(0);
        // Filter method (0)
        data.push(0);
        // Interlace method (0 = none)
        data.push(0);

        create_png_chunk(b"IHDR", &data)
    }

    /// Create PNG IDAT chunk with compressed image data
    fn create_idat_chunk(&self) -> Result<Vec<u8>> {
        // Prepare raw image data with filter bytes
        let mut raw_data = Vec::with_capacity((self.width as usize * 4 + 1) * self.height as usize);

        for y in 0..self.height as usize {
            // Filter byte (0 = None)
            raw_data.push(0);

            // Row pixels
            let row_start = y * self.width as usize * 4;
            let row_end = row_start + self.width as usize * 4;
            raw_data.extend_from_slice(&self.pixels[row_start..row_end]);
        }

        // Compress with zlib (deflate)
        let compressed = compress_zlib(&raw_data)?;

        Ok(create_png_chunk(b"IDAT", &compressed))
    }
}

/// Create PNG IEND chunk
fn create_iend_chunk() -> Vec<u8> {
    create_png_chunk(b"IEND", &[])
}

/// Create a PNG chunk with length, type, data, and CRC
fn create_png_chunk(chunk_type: &[u8; 4], data: &[u8]) -> Vec<u8> {
    let mut chunk = Vec::with_capacity(12 + data.len());

    // Length (4 bytes)
    chunk.extend_from_slice(&(data.len() as u32).to_be_bytes());

    // Type (4 bytes)
    chunk.extend_from_slice(chunk_type);

    // Data
    chunk.extend_from_slice(data);

    // CRC32 of type + data
    let crc = crc32_ieee(&chunk[4..]);
    chunk.extend_from_slice(&crc.to_be_bytes());

    chunk
}

/// Calculate CRC32 (IEEE polynomial)
fn crc32_ieee(data: &[u8]) -> u32 {
    let mut crc = 0xFFFFFFFFu32;
    for &byte in data {
        let index = ((crc ^ byte as u32) & 0xFF) as usize;
        crc = CRC32_TABLE[index] ^ (crc >> 8);
    }
    !crc
}

/// CRC32 lookup table (IEEE polynomial)
static CRC32_TABLE: [u32; 256] = {
    let mut table = [0u32; 256];
    let mut i = 0;
    while i < 256 {
        let mut crc = i as u32;
        let mut j = 0;
        while j < 8 {
            if crc & 1 != 0 {
                crc = 0xEDB88320 ^ (crc >> 1);
            } else {
                crc >>= 1;
            }
            j += 1;
        }
        table[i] = crc;
        i += 1;
    }
    table
};

/// Compress data using zlib/deflate
/// Uses fast compression (level 1) for speed during sprite extraction
fn compress_zlib(data: &[u8]) -> Result<Vec<u8>> {
    use flate2::write::ZlibEncoder;
    use flate2::Compression;

    // Use fast compression (level 1) instead of default (level 6)
    // This significantly speeds up sprite extraction with minimal size increase
    let mut encoder = ZlibEncoder::new(Vec::new(), Compression::fast());
    encoder
        .write_all(data)
        .map_err(|e| RustscapeError::Cache(CacheError::Io(format!("Compression failed: {}", e))))?;

    encoder
        .finish()
        .map_err(|e| RustscapeError::Cache(CacheError::Io(format!("Compression failed: {}", e))))
}

/// Sprite decoder for parsing cache sprite data
pub struct SpriteDecoder;

impl SpriteDecoder {
    /// Decode sprites from raw cache archive data
    ///
    /// A sprite archive can contain multiple frames (images).
    /// Returns all frames as individual Sprite objects.
    pub fn decode(archive_id: u32, data: &[u8]) -> Result<Vec<Sprite>> {
        if data.is_empty() {
            return Ok(vec![Sprite::empty(archive_id, 0)]);
        }

        let mut sprites = Vec::new();

        // Try to parse as a multi-frame sprite archive
        match Self::decode_sprite_archive(archive_id, data) {
            Ok(decoded) => sprites = decoded,
            Err(e) => {
                trace!("Failed to decode sprite {} as archive: {}", archive_id, e);
                // Return empty sprite on parse failure
                sprites.push(Sprite::empty(archive_id, 0));
            }
        }

        Ok(sprites)
    }

    /// Decode a sprite archive (multiple frames)
    fn decode_sprite_archive(archive_id: u32, data: &[u8]) -> Result<Vec<Sprite>> {
        if data.len() < 2 {
            return Err(RustscapeError::Cache(CacheError::InvalidData(
                "Sprite data too short".to_string(),
            )));
        }

        let _cursor = Cursor::new(data);
        let mut sprites = Vec::new();

        // Read sprite count from the end of the data
        let data_len = data.len();
        if data_len < 2 {
            return Err(RustscapeError::Cache(CacheError::InvalidData(
                "Sprite data too short for frame count".to_string(),
            )));
        }

        let frame_count = u16::from_be_bytes([data[data_len - 2], data[data_len - 1]]) as usize;

        if frame_count == 0 {
            return Ok(vec![Sprite::empty(archive_id, 0)]);
        }

        // Validate frame count
        if frame_count > 1000 {
            // Probably not a valid sprite archive, try single image
            return Self::decode_single_sprite(archive_id, data);
        }

        // Read metadata from the end of the file
        // Format: [frame data...] [widths: u16 * count] [heights: u16 * count]
        //         [offsetsX: u16 * count] [offsetsY: u16 * count]
        //         [palette: varies] [frame_count: u16]

        let metadata_size = 2 + frame_count * 8; // frame_count + 4 * 2 bytes per frame
        if data_len < metadata_size {
            return Self::decode_single_sprite(archive_id, data);
        }

        let metadata_start = data_len - metadata_size;

        // Read dimensions
        let mut widths = Vec::with_capacity(frame_count);
        let mut heights = Vec::with_capacity(frame_count);
        let mut offsets_x = Vec::with_capacity(frame_count);
        let mut offsets_y = Vec::with_capacity(frame_count);

        let mut pos = metadata_start;

        for _ in 0..frame_count {
            if pos + 2 > data_len - 2 {
                break;
            }
            widths.push(u16::from_be_bytes([data[pos], data[pos + 1]]));
            pos += 2;
        }

        for _ in 0..frame_count {
            if pos + 2 > data_len - 2 {
                break;
            }
            heights.push(u16::from_be_bytes([data[pos], data[pos + 1]]));
            pos += 2;
        }

        for _ in 0..frame_count {
            if pos + 2 > data_len - 2 {
                break;
            }
            offsets_x.push(i16::from_be_bytes([data[pos], data[pos + 1]]));
            pos += 2;
        }

        for _ in 0..frame_count {
            if pos + 2 > data_len - 2 {
                break;
            }
            offsets_y.push(i16::from_be_bytes([data[pos], data[pos + 1]]));
            pos += 2;
        }

        // Validate we got all dimensions
        if widths.len() != frame_count
            || heights.len() != frame_count
            || offsets_x.len() != frame_count
            || offsets_y.len() != frame_count
        {
            return Self::decode_single_sprite(archive_id, data);
        }

        // Read palette (after dimensions, before frame count)
        // Palette format: [palette_size: u8] [colors: RGB * palette_size]
        // Note: This is a simplified approach; actual format may vary

        // For now, try to decode each frame
        let mut pixel_offset = 0;
        for frame_idx in 0..frame_count {
            let width = widths[frame_idx] as u32;
            let height = heights[frame_idx] as u32;
            let offset_x = offsets_x[frame_idx] as i32;
            let offset_y = offsets_y[frame_idx] as i32;

            if width == 0 || height == 0 {
                sprites.push(Sprite::empty(archive_id, frame_idx as u32));
                continue;
            }

            let pixel_count = (width * height) as usize;

            // Create sprite with placeholder pixels for now
            // Full decoding requires palette extraction
            let mut sprite = Sprite {
                id: archive_id,
                frame: frame_idx as u32,
                width,
                height,
                offset_x,
                offset_y,
                pixels: vec![0; pixel_count * 4],
            };

            // Try to read indexed pixel data and convert to RGBA
            // This is a simplified version - actual implementation needs palette
            if pixel_offset + pixel_count <= metadata_start {
                for i in 0..pixel_count {
                    let idx = pixel_offset + i;
                    if idx < data.len() {
                        let color_idx = data[idx];
                        // Placeholder: grayscale based on index
                        let gray = color_idx;
                        sprite.pixels[i * 4] = gray;
                        sprite.pixels[i * 4 + 1] = gray;
                        sprite.pixels[i * 4 + 2] = gray;
                        sprite.pixels[i * 4 + 3] = if color_idx == 0 { 0 } else { 255 };
                    }
                }
                pixel_offset += pixel_count;
            }

            sprites.push(sprite);
        }

        if sprites.is_empty() {
            sprites.push(Sprite::empty(archive_id, 0));
        }

        Ok(sprites)
    }

    /// Decode a single sprite (not a multi-frame archive)
    fn decode_single_sprite(archive_id: u32, data: &[u8]) -> Result<Vec<Sprite>> {
        // Try to decode as a single indexed color image
        // Format varies, but typically:
        // [width: u16] [height: u16] [palette_size: u8] [palette: RGB * size] [pixels: indices]

        if data.len() < 5 {
            return Ok(vec![Sprite::empty(archive_id, 0)]);
        }

        let width = u16::from_be_bytes([data[0], data[1]]) as u32;
        let height = u16::from_be_bytes([data[2], data[3]]) as u32;

        // Sanity check dimensions
        if width == 0 || height == 0 || width > 4096 || height > 4096 {
            return Ok(vec![Sprite::empty(archive_id, 0)]);
        }

        let pixel_count = (width * height) as usize;
        let palette_size = data[4] as usize;

        let palette_start = 5;
        let palette_end = palette_start + palette_size * 3;

        if palette_end > data.len() || palette_end + pixel_count > data.len() {
            // Data doesn't match expected format
            return Ok(vec![Sprite::empty(archive_id, 0)]);
        }

        // Read palette
        let mut palette = Vec::with_capacity(palette_size);
        for i in 0..palette_size {
            let idx = palette_start + i * 3;
            palette.push([data[idx], data[idx + 1], data[idx + 2]]);
        }

        // Read pixel indices and convert to RGBA
        let pixels_start = palette_end;
        let mut pixels = vec![0u8; pixel_count * 4];

        for i in 0..pixel_count {
            let color_idx = data[pixels_start + i] as usize;
            if color_idx == 0 {
                // Index 0 is typically transparent
                pixels[i * 4] = 0;
                pixels[i * 4 + 1] = 0;
                pixels[i * 4 + 2] = 0;
                pixels[i * 4 + 3] = 0;
            } else if color_idx < palette.len() {
                let color = palette[color_idx];
                pixels[i * 4] = color[0];
                pixels[i * 4 + 1] = color[1];
                pixels[i * 4 + 2] = color[2];
                pixels[i * 4 + 3] = 255;
            }
        }

        Ok(vec![Sprite {
            id: archive_id,
            frame: 0,
            width,
            height,
            offset_x: 0,
            offset_y: 0,
            pixels,
        }])
    }
}

/// Sprite exporter for batch exporting sprites from the cache
pub struct SpriteExporter {
    /// Output directory for exported sprites
    output_dir: std::path::PathBuf,
    /// Number of sprites exported (atomic for thread safety)
    exported_count: Arc<AtomicU32>,
    /// Number of sprites failed (atomic for thread safety)
    failed_count: Arc<AtomicU32>,
}

impl SpriteExporter {
    /// Create a new sprite exporter
    pub fn new(output_dir: impl AsRef<Path>) -> Self {
        Self {
            output_dir: output_dir.as_ref().to_path_buf(),
            exported_count: Arc::new(AtomicU32::new(0)),
            failed_count: Arc::new(AtomicU32::new(0)),
        }
    }

    /// Export a single sprite (thread-safe)
    pub fn export_sprite(&self, sprite: &Sprite, subdir: &str) -> Result<()> {
        if !sprite.is_valid() {
            self.failed_count.fetch_add(1, Ordering::Relaxed);
            return Ok(()); // Skip invalid sprites silently
        }

        let filename = if sprite.frame == 0 {
            format!("{}.png", sprite.id)
        } else {
            format!("{}_{}.png", sprite.id, sprite.frame)
        };

        let output_path = self.output_dir.join(subdir).join(&filename);

        match sprite.export_png(&output_path) {
            Ok(()) => {
                self.exported_count.fetch_add(1, Ordering::Relaxed);
                trace!("Exported sprite to {:?}", output_path);
                Ok(())
            }
            Err(e) => {
                self.failed_count.fetch_add(1, Ordering::Relaxed);
                warn!("Failed to export sprite {}: {}", sprite.id, e);
                Err(e)
            }
        }
    }

    /// Export multiple sprites sequentially
    pub fn export_sprites(&self, sprites: &[Sprite], subdir: &str) -> Result<()> {
        for sprite in sprites {
            let _ = self.export_sprite(sprite, subdir);
        }
        Ok(())
    }

    /// Export multiple sprites in parallel using Rayon
    ///
    /// This provides significant speedup on multi-core systems by
    /// parallelizing both PNG encoding and file I/O across threads.
    pub fn export_sprites_parallel(&self, sprites: &[Sprite], subdir: &str) {
        // Ensure output directory exists before parallel writes
        let output_dir = self.output_dir.join(subdir);
        let _ = fs::create_dir_all(&output_dir);

        sprites.par_iter().for_each(|sprite| {
            let _ = self.export_sprite(sprite, subdir);
        });
    }

    /// Get the number of successfully exported sprites
    pub fn exported_count(&self) -> u32 {
        self.exported_count.load(Ordering::Relaxed)
    }

    /// Get the number of failed exports
    pub fn failed_count(&self) -> u32 {
        self.failed_count.load(Ordering::Relaxed)
    }

    /// Get the output directory
    pub fn output_dir(&self) -> &Path {
        &self.output_dir
    }
}

/// Result of a parallel archive extraction job
pub struct ArchiveExtractionJob {
    pub archive_id: u32,
    pub data: Vec<u8>,
}

/// Extract and export sprites from multiple archives in parallel
///
/// This is the highest-performance extraction method, parallelizing:
/// 1. Sprite decoding from archives
/// 2. PNG encoding
/// 3. File I/O
///
/// Returns the number of sprites exported and failed.
pub fn extract_archives_parallel(
    archives: Vec<ArchiveExtractionJob>,
    exporter: &SpriteExporter,
    subdir: &str,
) -> (u32, u32) {
    // Ensure output directory exists
    let output_dir = exporter.output_dir.join(subdir);
    let _ = fs::create_dir_all(&output_dir);

    // Process archives in parallel
    archives.par_iter().for_each(|job| {
        if job.data.is_empty() {
            return;
        }

        // Decode sprites from archive
        if let Ok(sprites) = SpriteDecoder::decode(job.archive_id, &job.data) {
            // Export each sprite (this is already parallelized at the archive level)
            for sprite in &sprites {
                let _ = exporter.export_sprite(sprite, subdir);
            }
        }
    });

    (exporter.exported_count(), exporter.failed_count())
}

/// Batch decode sprites from multiple archives in parallel
///
/// Returns a vector of all decoded sprites from all archives.
/// Useful when you want to process sprites further before exporting.
pub fn decode_archives_parallel(archives: Vec<ArchiveExtractionJob>) -> Vec<Sprite> {
    archives
        .par_iter()
        .filter(|job| !job.data.is_empty())
        .flat_map(|job| SpriteDecoder::decode(job.archive_id, &job.data).unwrap_or_default())
        .collect()
}

/// Sprite manifest entry for the web client
#[derive(Debug, Clone, serde::Serialize)]
pub struct SpriteManifestEntry {
    /// Sprite ID
    pub id: u32,
    /// Frame index
    pub frame: u32,
    /// Image width
    pub width: u32,
    /// Image height
    pub height: u32,
    /// X offset
    pub offset_x: i32,
    /// Y offset
    pub offset_y: i32,
    /// File path relative to sprites directory
    pub path: String,
}

/// Sprite manifest for the web client
#[derive(Debug, Clone, serde::Serialize)]
pub struct SpriteManifest {
    /// Index type (e.g., "sprites", "textures")
    pub index_type: String,
    /// Total number of sprites
    pub count: usize,
    /// Sprite entries
    pub sprites: Vec<SpriteManifestEntry>,
}

impl SpriteManifest {
    /// Create a new manifest
    pub fn new(index_type: &str) -> Self {
        Self {
            index_type: index_type.to_string(),
            count: 0,
            sprites: Vec::new(),
        }
    }

    /// Add a sprite to the manifest
    pub fn add_sprite(&mut self, sprite: &Sprite, subdir: &str) {
        if !sprite.is_valid() {
            return;
        }

        let filename = if sprite.frame == 0 {
            format!("{}.png", sprite.id)
        } else {
            format!("{}_{}.png", sprite.id, sprite.frame)
        };

        self.sprites.push(SpriteManifestEntry {
            id: sprite.id,
            frame: sprite.frame,
            width: sprite.width,
            height: sprite.height,
            offset_x: sprite.offset_x,
            offset_y: sprite.offset_y,
            path: format!("{}/{}", subdir, filename),
        });

        self.count = self.sprites.len();
    }

    /// Save the manifest as JSON
    pub fn save(&self, output_path: &Path) -> Result<()> {
        let json = serde_json::to_string_pretty(self).map_err(|e| {
            RustscapeError::Cache(CacheError::InvalidData(format!(
                "Failed to serialize manifest: {}",
                e
            )))
        })?;

        if let Some(parent) = output_path.parent() {
            fs::create_dir_all(parent).map_err(|e| {
                RustscapeError::Cache(CacheError::Io(format!("Failed to create directory: {}", e)))
            })?;
        }

        fs::write(output_path, json).map_err(|e| {
            RustscapeError::Cache(CacheError::Io(format!("Failed to write manifest: {}", e)))
        })?;

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_empty_sprite() {
        let sprite = Sprite::empty(123, 0);
        assert_eq!(sprite.id, 123);
        assert_eq!(sprite.frame, 0);
        assert_eq!(sprite.width, 0);
        assert_eq!(sprite.height, 0);
        assert!(!sprite.is_valid());
    }

    #[test]
    fn test_sprite_validity() {
        let mut sprite = Sprite::empty(1, 0);
        assert!(!sprite.is_valid());

        sprite.width = 10;
        sprite.height = 10;
        assert!(!sprite.is_valid()); // No pixels

        sprite.pixels = vec![0; 400]; // 10x10x4
        assert!(sprite.is_valid());
    }

    #[test]
    fn test_crc32() {
        // Test with known value
        let data = b"123456789";
        let crc = crc32_ieee(data);
        assert_eq!(crc, 0xCBF43926);
    }

    #[test]
    fn test_png_chunk_creation() {
        let chunk = create_png_chunk(b"TEST", &[1, 2, 3, 4]);
        assert_eq!(chunk.len(), 16); // 4 (len) + 4 (type) + 4 (data) + 4 (crc)
        assert_eq!(&chunk[0..4], &[0, 0, 0, 4]); // Length = 4
        assert_eq!(&chunk[4..8], b"TEST"); // Type
        assert_eq!(&chunk[8..12], &[1, 2, 3, 4]); // Data
    }

    #[test]
    fn test_sprite_decoder_empty() {
        let sprites = SpriteDecoder::decode(0, &[]).unwrap();
        assert_eq!(sprites.len(), 1);
        assert!(!sprites[0].is_valid());
    }

    #[test]
    fn test_sprite_manifest() {
        let mut manifest = SpriteManifest::new("sprites");
        assert_eq!(manifest.count, 0);

        let sprite = Sprite {
            id: 1,
            frame: 0,
            width: 32,
            height: 32,
            offset_x: 0,
            offset_y: 0,
            pixels: vec![0; 32 * 32 * 4],
        };

        manifest.add_sprite(&sprite, "ui");
        assert_eq!(manifest.count, 1);
        assert_eq!(manifest.sprites[0].id, 1);
        assert_eq!(manifest.sprites[0].path, "ui/1.png");
    }
}
