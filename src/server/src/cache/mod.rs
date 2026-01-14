//! Cache module
//!
//! Handles loading and serving game cache files. The cache contains all
//! game assets including:
//! - Models and textures
//! - Maps and regions
//! - Item, NPC, and object definitions
//! - Interfaces and sprites
//! - Music and sound effects
//! - Scripts and configurations
//!
//! The cache is organized into indices (0-254) with each index containing
//! multiple archives. Index 255 is special and contains reference tables
//! for all other indices.
//!
//! ## Cache File Structure
//!
//! The Jagex cache consists of:
//! - `main_file_cache.dat2` - The main data file containing all archive data
//! - `main_file_cache.idx0` through `main_file_cache.idx254` - Index files for each cache index
//! - `main_file_cache.idx255` - The reference index containing metadata for all other indices
//!
//! Each index file contains 6-byte entries:
//! - Bytes 0-2: Container size (24-bit big-endian)
//! - Bytes 3-5: Sector number (24-bit big-endian)
//!
//! The data file is divided into 520-byte sectors:
//! - Bytes 0-1: Container ID (16-bit big-endian)
//! - Bytes 2-3: Part number (16-bit big-endian)
//! - Bytes 4-6: Next sector (24-bit big-endian)
//! - Byte 7: Index ID
//! - Bytes 8-519: Data (512 bytes)

pub mod sprites;

use std::collections::HashMap;
use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::path::{Path, PathBuf};
use std::sync::RwLock;

use bzip2::read::BzDecoder;
use flate2::read::GzDecoder;
use tracing::{debug, info, trace, warn};

use crate::error::Result;

/// Number of cache indices (0-28 for revision 530)
pub const INDEX_COUNT: usize = 29;

/// Size of each sector in the data file
const SECTOR_SIZE: usize = 520;

/// Size of the sector header
const SECTOR_HEADER_SIZE: usize = 8;

/// Size of data per sector (excluding header)
const SECTOR_DATA_SIZE: usize = 512;

/// Size of each index entry
const INDEX_ENTRY_SIZE: usize = 6;

/// Maximum container size (5MB)
const MAX_CONTAINER_SIZE: usize = 5_000_000;

/// Compression types
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum CompressionType {
    /// No compression
    None = 0,
    /// BZIP2 compression
    Bzip2 = 1,
    /// GZIP compression
    Gzip = 2,
    /// LZMA compression (rare, used in some revisions)
    Lzma = 3,
}

impl CompressionType {
    /// Create from a byte value
    pub fn from_u8(value: u8) -> Option<Self> {
        match value {
            0 => Some(Self::None),
            1 => Some(Self::Bzip2),
            2 => Some(Self::Gzip),
            3 => Some(Self::Lzma),
            _ => None,
        }
    }
}

/// Reference table information for an index
#[derive(Debug, Clone)]
pub struct ReferenceTable {
    /// Protocol version (5, 6, or 7)
    pub protocol: u8,
    /// Revision number (if protocol >= 6)
    pub revision: u32,
    /// Whether archives have name hashes
    pub named: bool,
    /// Whether archives have whirlpool digests
    pub whirlpool: bool,
    /// Archive information
    pub archives: Vec<ArchiveInfo>,
    /// CRC32 of the reference table data
    pub crc: u32,
}

/// Information about an archive within an index
#[derive(Debug, Clone, Default)]
pub struct ArchiveInfo {
    /// Archive ID
    pub id: u32,
    /// Name hash (if named)
    pub name_hash: i32,
    /// CRC32 checksum
    pub crc: u32,
    /// Version number
    pub version: u32,
    /// Whirlpool digest (if whirlpool enabled)
    pub whirlpool: Option<[u8; 64]>,
    /// File IDs within this archive
    pub file_ids: Vec<u32>,
}

/// Cache file entry (raw container data)
#[derive(Debug, Clone)]
pub struct CacheEntry {
    /// Index ID
    pub index: u8,
    /// Archive ID
    pub archive: u32,
    /// Raw container data (includes compression header)
    pub data: Vec<u8>,
}

/// Cache store for serving game files
pub struct CacheStore {
    /// Path to the cache directory
    path: PathBuf,
    /// The main data file
    data_file: RwLock<Option<File>>,
    /// Index files (0-255)
    index_files: RwLock<HashMap<u8, File>>,
    /// Cached reference tables
    reference_tables: RwLock<HashMap<u8, ReferenceTable>>,
    /// Cached checksum table
    checksum_table: RwLock<Option<Vec<u8>>>,
    /// Raw reference table data (for serving to clients)
    raw_reference_data: RwLock<HashMap<u8, Vec<u8>>>,
    /// Whether the cache has been loaded
    loaded: RwLock<bool>,
    /// Number of indices available
    index_count: RwLock<usize>,
}

impl CacheStore {
    /// Create a new cache store
    pub fn new(path: impl AsRef<Path>) -> Result<Self> {
        let path = path.as_ref().to_path_buf();

        info!(path = %path.display(), "Initializing cache store");

        let store = Self {
            path,
            data_file: RwLock::new(None),
            index_files: RwLock::new(HashMap::new()),
            reference_tables: RwLock::new(HashMap::new()),
            checksum_table: RwLock::new(None),
            raw_reference_data: RwLock::new(HashMap::new()),
            loaded: RwLock::new(false),
            index_count: RwLock::new(0),
        };

        // Try to load the cache
        match store.load() {
            Ok(()) => {
                info!("Cache loaded successfully from real files");
            }
            Err(e) => {
                debug!("Cache not loaded, using stubs: {}", e);
            }
        }

        Ok(store)
    }

    /// Load the cache from disk
    fn load(&self) -> std::result::Result<(), String> {
        if !self.path.exists() {
            warn!(path = %self.path.display(), "Cache directory does not exist");
            return Err("Cache directory does not exist".to_string());
        }

        // Check for main data file
        let data_path = self.path.join("main_file_cache.dat2");
        if !data_path.exists() {
            debug!("Cache main file not found at {:?}", data_path);
            return Err("Cache main file not found".to_string());
        }

        // Open the main data file
        let data_file =
            File::open(&data_path).map_err(|e| format!("Failed to open data file: {}", e))?;
        *self.data_file.write().unwrap() = Some(data_file);

        // Open index file 255 (reference index)
        let idx255_path = self.path.join("main_file_cache.idx255");
        if !idx255_path.exists() {
            return Err("Reference index file (idx255) not found".to_string());
        }

        let idx255_file =
            File::open(&idx255_path).map_err(|e| format!("Failed to open idx255: {}", e))?;

        // Determine how many indices exist based on idx255 length
        let idx255_len = idx255_file
            .metadata()
            .map_err(|e| format!("Failed to get idx255 metadata: {}", e))?
            .len() as usize;
        let num_indices = idx255_len / INDEX_ENTRY_SIZE;

        info!("Found {} indices in reference file", num_indices);
        *self.index_count.write().unwrap() = num_indices;

        {
            let mut index_files = self.index_files.write().unwrap();
            index_files.insert(255, idx255_file);

            // Open all available index files
            for i in 0..num_indices {
                let idx_path = self.path.join(format!("main_file_cache.idx{}", i));
                if idx_path.exists() {
                    match File::open(&idx_path) {
                        Ok(file) => {
                            index_files.insert(i as u8, file);
                            trace!("Opened index file {}", i);
                        }
                        Err(e) => {
                            warn!("Failed to open index file {}: {}", i, e);
                        }
                    }
                }
            }

            info!("Opened {} index files", index_files.len());
        }

        // Load reference tables for all indices
        self.load_reference_tables()?;

        // Generate checksum table
        self.generate_checksum_table()?;

        *self.loaded.write().unwrap() = true;

        Ok(())
    }

    /// Load reference tables for all indices
    fn load_reference_tables(&self) -> std::result::Result<(), String> {
        let index_count = *self.index_count.read().unwrap();

        for i in 0..index_count {
            match self.read_container_data(255, i as u32) {
                Ok(data) => {
                    // Store raw data for serving to clients
                    self.raw_reference_data
                        .write()
                        .unwrap()
                        .insert(i as u8, data.clone());

                    // Parse reference table
                    match self.parse_reference_table(&data) {
                        Ok(table) => {
                            trace!(
                                "Loaded reference table for index {} with {} archives",
                                i,
                                table.archives.len()
                            );
                            self.reference_tables
                                .write()
                                .unwrap()
                                .insert(i as u8, table);
                        }
                        Err(e) => {
                            debug!("Failed to parse reference table for index {}: {}", i, e);
                        }
                    }
                }
                Err(e) => {
                    debug!("Failed to read reference table for index {}: {}", i, e);
                }
            }
        }

        let count = self.reference_tables.read().unwrap().len();
        info!("Loaded {} reference tables", count);

        Ok(())
    }

    /// Read raw container data from the cache
    fn read_container_data(&self, index: u8, archive: u32) -> std::result::Result<Vec<u8>, String> {
        let index_files = self.index_files.read().unwrap();
        let data_file_guard = self.data_file.read().unwrap();

        let index_file = index_files
            .get(&index)
            .ok_or_else(|| format!("Index file {} not found", index))?;

        let data_file = data_file_guard.as_ref().ok_or("Data file not loaded")?;

        // Read index entry (6 bytes)
        let mut index_entry = [0u8; INDEX_ENTRY_SIZE];
        let entry_offset = (archive as u64) * (INDEX_ENTRY_SIZE as u64);

        // Clone file handles for seeking (File doesn't implement Clone, so we need to work around)
        let mut index_reader = index_file
            .try_clone()
            .map_err(|e| format!("Failed to clone index file: {}", e))?;

        index_reader
            .seek(SeekFrom::Start(entry_offset))
            .map_err(|e| format!("Failed to seek in index file: {}", e))?;

        index_reader
            .read_exact(&mut index_entry)
            .map_err(|e| format!("Failed to read index entry: {}", e))?;

        // Parse index entry
        // Container size: bytes 0-2 (24-bit big-endian)
        let container_size = ((index_entry[0] as usize) << 16)
            | ((index_entry[1] as usize) << 8)
            | (index_entry[2] as usize);

        // Sector number: bytes 3-5 (24-bit big-endian)
        let mut sector = ((index_entry[3] as u32) << 16)
            | ((index_entry[4] as u32) << 8)
            | (index_entry[5] as u32);

        if container_size == 0 || container_size > MAX_CONTAINER_SIZE {
            return Err(format!("Invalid container size: {}", container_size));
        }

        if sector == 0 {
            return Err("Invalid sector: 0".to_string());
        }

        // Read container data from sectors
        let mut container_data = vec![0u8; container_size];
        let mut bytes_read = 0;
        let mut part = 0u16;

        let mut data_reader = data_file
            .try_clone()
            .map_err(|e| format!("Failed to clone data file: {}", e))?;

        while bytes_read < container_size {
            // Seek to sector
            let sector_offset = (sector as u64) * (SECTOR_SIZE as u64);
            data_reader
                .seek(SeekFrom::Start(sector_offset))
                .map_err(|e| format!("Failed to seek to sector {}: {}", sector, e))?;

            // Read sector header and data
            let mut sector_buffer = [0u8; SECTOR_SIZE];
            let bytes_to_read = std::cmp::min(
                SECTOR_SIZE,
                container_size - bytes_read + SECTOR_HEADER_SIZE,
            );
            data_reader
                .read_exact(&mut sector_buffer[..bytes_to_read])
                .map_err(|e| format!("Failed to read sector {}: {}", sector, e))?;

            // Parse sector header
            let sector_archive_id = ((sector_buffer[0] as u32) << 8) | (sector_buffer[1] as u32);
            let sector_part = ((sector_buffer[2] as u16) << 8) | (sector_buffer[3] as u16);
            let next_sector = ((sector_buffer[4] as u32) << 16)
                | ((sector_buffer[5] as u32) << 8)
                | (sector_buffer[6] as u32);
            let sector_index = sector_buffer[7];

            // Validate sector header
            if sector_archive_id != archive {
                return Err(format!(
                    "Archive ID mismatch: expected {}, got {}",
                    archive, sector_archive_id
                ));
            }
            if sector_part != part {
                return Err(format!(
                    "Part mismatch: expected {}, got {}",
                    part, sector_part
                ));
            }
            if sector_index != index {
                return Err(format!(
                    "Index mismatch: expected {}, got {}",
                    index, sector_index
                ));
            }

            // Copy data from sector
            let data_in_sector = std::cmp::min(SECTOR_DATA_SIZE, container_size - bytes_read);
            container_data[bytes_read..bytes_read + data_in_sector].copy_from_slice(
                &sector_buffer[SECTOR_HEADER_SIZE..SECTOR_HEADER_SIZE + data_in_sector],
            );

            bytes_read += data_in_sector;
            part += 1;
            sector = next_sector;

            if bytes_read < container_size && sector == 0 {
                return Err("Unexpected end of sector chain".to_string());
            }
        }

        Ok(container_data)
    }

    /// Decompress container data
    fn decompress_container(&self, data: &[u8]) -> std::result::Result<Vec<u8>, String> {
        if data.len() < 5 {
            return Err("Container data too short".to_string());
        }

        let compression = data[0];
        let compressed_size = u32::from_be_bytes([data[1], data[2], data[3], data[4]]) as usize;

        match CompressionType::from_u8(compression) {
            Some(CompressionType::None) => {
                // No compression - just return the data after header
                if data.len() < 5 + compressed_size {
                    return Err("Insufficient data for uncompressed container".to_string());
                }
                Ok(data[5..5 + compressed_size].to_vec())
            }
            Some(CompressionType::Bzip2) => {
                if data.len() < 9 {
                    return Err("Bzip2 container too short".to_string());
                }
                let decompressed_size =
                    u32::from_be_bytes([data[5], data[6], data[7], data[8]]) as usize;

                // Bzip2 data starts at offset 9, prepend "BZ" header
                let mut bzip_data = vec![b'B', b'Z', b'h', b'1'];
                bzip_data.extend_from_slice(&data[9..5 + compressed_size]);

                let mut decoder = BzDecoder::new(&bzip_data[..]);
                let mut decompressed = vec![0u8; decompressed_size];
                decoder
                    .read_exact(&mut decompressed)
                    .map_err(|e| format!("Bzip2 decompression failed: {}", e))?;

                Ok(decompressed)
            }
            Some(CompressionType::Gzip) => {
                if data.len() < 9 {
                    return Err("Gzip container too short".to_string());
                }
                let decompressed_size =
                    u32::from_be_bytes([data[5], data[6], data[7], data[8]]) as usize;

                let mut decoder = GzDecoder::new(&data[9..5 + compressed_size]);
                let mut decompressed = vec![0u8; decompressed_size];
                decoder
                    .read_exact(&mut decompressed)
                    .map_err(|e| format!("Gzip decompression failed: {}", e))?;

                Ok(decompressed)
            }
            Some(CompressionType::Lzma) => {
                // LZMA decompression would require lzma-rs crate
                Err("LZMA decompression not yet implemented".to_string())
            }
            None => Err(format!("Unknown compression type: {}", compression)),
        }
    }

    /// Parse a reference table from decompressed data
    fn parse_reference_table(
        &self,
        raw_data: &[u8],
    ) -> std::result::Result<ReferenceTable, String> {
        // First decompress the container
        let data = self.decompress_container(raw_data)?;

        if data.is_empty() {
            return Err("Empty reference table data".to_string());
        }

        let mut pos = 0;

        // Protocol version
        let protocol = data[pos];
        pos += 1;

        if protocol < 5 || protocol > 7 {
            return Err(format!(
                "Unsupported reference table protocol: {}",
                protocol
            ));
        }

        // Revision (only in protocol >= 6)
        let revision = if protocol >= 6 {
            let rev = u32::from_be_bytes([data[pos], data[pos + 1], data[pos + 2], data[pos + 3]]);
            pos += 4;
            rev
        } else {
            0
        };

        // Flags
        let flags = data[pos];
        pos += 1;
        let named = (flags & 0x01) != 0;
        let whirlpool = (flags & 0x02) != 0;

        // Number of archives
        let archive_count = if protocol >= 7 {
            self.read_big_smart(&data, &mut pos)?
        } else {
            let count = u16::from_be_bytes([data[pos], data[pos + 1]]) as u32;
            pos += 2;
            count
        };

        if archive_count == 0 {
            return Ok(ReferenceTable {
                protocol,
                revision,
                named,
                whirlpool,
                archives: Vec::new(),
                crc: crc32fast::hash(raw_data),
            });
        }

        // Read archive IDs (delta-encoded)
        let mut archive_ids = Vec::with_capacity(archive_count as usize);
        let mut last_id = 0u32;
        for _ in 0..archive_count {
            let delta = if protocol >= 7 {
                self.read_big_smart(&data, &mut pos)?
            } else {
                let d = u16::from_be_bytes([data[pos], data[pos + 1]]) as u32;
                pos += 2;
                d
            };
            last_id += delta;
            archive_ids.push(last_id);
        }

        // Initialize archive info
        let mut archives: Vec<ArchiveInfo> = archive_ids
            .iter()
            .map(|&id| ArchiveInfo {
                id,
                ..Default::default()
            })
            .collect();

        // Read name hashes (if named)
        if named {
            for archive in &mut archives {
                archive.name_hash =
                    i32::from_be_bytes([data[pos], data[pos + 1], data[pos + 2], data[pos + 3]]);
                pos += 4;
            }
        }

        // Read whirlpool digests (if whirlpool)
        if whirlpool {
            for archive in &mut archives {
                let mut digest = [0u8; 64];
                digest.copy_from_slice(&data[pos..pos + 64]);
                pos += 64;
                archive.whirlpool = Some(digest);
            }
        }

        // Read CRC32 values
        for archive in &mut archives {
            archive.crc =
                u32::from_be_bytes([data[pos], data[pos + 1], data[pos + 2], data[pos + 3]]);
            pos += 4;
        }

        // Read version numbers
        for archive in &mut archives {
            archive.version =
                u32::from_be_bytes([data[pos], data[pos + 1], data[pos + 2], data[pos + 3]]);
            pos += 4;
        }

        // Read file counts
        let mut file_counts = Vec::with_capacity(archives.len());
        for _ in 0..archives.len() {
            let count = if protocol >= 7 {
                self.read_big_smart(&data, &mut pos)?
            } else {
                let c = u16::from_be_bytes([data[pos], data[pos + 1]]) as u32;
                pos += 2;
                c
            };
            file_counts.push(count);
        }

        // Read file IDs (delta-encoded)
        for (i, archive) in archives.iter_mut().enumerate() {
            let mut file_ids = Vec::with_capacity(file_counts[i] as usize);
            let mut last_file_id = 0u32;
            for _ in 0..file_counts[i] {
                let delta = if protocol >= 7 {
                    self.read_big_smart(&data, &mut pos)?
                } else {
                    let d = u16::from_be_bytes([data[pos], data[pos + 1]]) as u32;
                    pos += 2;
                    d
                };
                last_file_id += delta;
                file_ids.push(last_file_id);
            }
            archive.file_ids = file_ids;
        }

        // Calculate CRC of raw data
        let crc = crc32fast::hash(raw_data);

        Ok(ReferenceTable {
            protocol,
            revision,
            named,
            whirlpool,
            archives,
            crc,
        })
    }

    /// Read a "big smart" value (variable-length encoding)
    fn read_big_smart(&self, data: &[u8], pos: &mut usize) -> std::result::Result<u32, String> {
        if *pos >= data.len() {
            return Err("Unexpected end of data".to_string());
        }

        if data[*pos] < 0x80 {
            // Single byte value (0-127)
            let val = (data[*pos] as u32) << 8;
            *pos += 1;
            if *pos >= data.len() {
                return Err("Unexpected end of data".to_string());
            }
            let val = val | (data[*pos] as u32);
            *pos += 1;
            Ok(val)
        } else {
            // Four byte value
            let val = u32::from_be_bytes([
                data[*pos] & 0x7F,
                data[*pos + 1],
                data[*pos + 2],
                data[*pos + 3],
            ]);
            *pos += 4;
            Ok(val)
        }
    }

    /// Generate the checksum table (served as index 255, archive 255)
    fn generate_checksum_table(&self) -> std::result::Result<(), String> {
        let reference_tables = self.reference_tables.read().unwrap();
        let raw_data = self.raw_reference_data.read().unwrap();
        let index_count = *self.index_count.read().unwrap();

        // Checksum table format: for each index (0 to index_count-1):
        // - CRC32 (4 bytes, big-endian)
        // - Version/Revision (4 bytes, big-endian)
        let mut table = Vec::with_capacity(index_count * 8);

        for i in 0..index_count {
            let i = i as u8;
            if let Some(ref_table) = reference_tables.get(&i) {
                // Use actual CRC from reference table
                table.extend_from_slice(&ref_table.crc.to_be_bytes());
                table.extend_from_slice(&ref_table.revision.to_be_bytes());
            } else if let Some(data) = raw_data.get(&i) {
                // Calculate CRC from raw data
                let crc = crc32fast::hash(data);
                table.extend_from_slice(&crc.to_be_bytes());
                table.extend_from_slice(&0u32.to_be_bytes());
            } else {
                // Stub values
                table.extend_from_slice(&0u32.to_be_bytes());
                table.extend_from_slice(&0u32.to_be_bytes());
            }
        }

        *self.checksum_table.write().unwrap() = Some(table);
        info!("Generated checksum table with {} entries", index_count);

        Ok(())
    }

    /// Check if the cache is loaded
    pub fn is_loaded(&self) -> bool {
        *self.loaded.read().unwrap()
    }

    /// Get the checksum table (index 255, archive 255)
    pub fn get_checksum_table(&self) -> Result<Vec<u8>> {
        // Check cache first
        if let Some(data) = self.checksum_table.read().unwrap().as_ref() {
            return Ok(data.clone());
        }

        // Generate stub checksum table
        let data = self.generate_stub_checksum_table();

        // Cache it
        *self.checksum_table.write().unwrap() = Some(data.clone());

        Ok(data)
    }

    /// Get a reference table (index 255, archive 0-254)
    pub fn get_reference_table(&self, index: u8) -> Result<Vec<u8>> {
        // Check if we have real reference data
        if let Some(data) = self.raw_reference_data.read().unwrap().get(&index) {
            return Ok(data.clone());
        }

        // Generate stub reference table
        Ok(self.generate_stub_reference_table(index))
    }

    /// Get a file from the cache (raw container data for JS5)
    pub fn get_file(&self, index: u8, archive: u32) -> Result<Vec<u8>> {
        // Try to read from real cache
        if self.is_loaded() {
            match self.read_container_data(index, archive) {
                Ok(data) => return Ok(data),
                Err(e) => {
                    trace!("Failed to read container {}/{}: {}", index, archive, e);
                }
            }
        }

        // Return stub data
        Ok(self.generate_stub_file(index, archive))
    }

    /// Get parsed reference table for an index
    pub fn get_parsed_reference_table(&self, index: u8) -> Option<ReferenceTable> {
        self.reference_tables.read().unwrap().get(&index).cloned()
    }

    /// Generate a stub checksum table
    fn generate_stub_checksum_table(&self) -> Vec<u8> {
        let mut data = Vec::with_capacity(INDEX_COUNT * 8);

        for i in 0..INDEX_COUNT {
            // CRC32 (stub value based on index)
            data.extend_from_slice(&(i as u32).to_be_bytes());
            // Version (stub value)
            data.extend_from_slice(&1u32.to_be_bytes());
        }

        data
    }

    /// Generate a stub reference table for an index
    fn generate_stub_reference_table(&self, _index: u8) -> Vec<u8> {
        // Reference table format (simplified):
        // - Compression (1 byte) = 0 (no compression)
        // - Length (4 bytes) = length of uncompressed data
        // - Format (1 byte) = 5 (no version field)
        // - Flags (1 byte) = 0 (no names, no digests)
        // - Archive count (2 bytes) = 0

        let mut data = Vec::with_capacity(10);

        // Compression: none
        data.push(0);

        // Length: 3 bytes
        data.extend_from_slice(&3u32.to_be_bytes());

        // Format version (5 = no version field)
        data.push(5);

        // Flags (no names, no digests)
        data.push(0);

        // Archive count (0)
        data.extend_from_slice(&0u16.to_be_bytes());

        data
    }

    /// Generate stub file data
    fn generate_stub_file(&self, _index: u8, _archive: u32) -> Vec<u8> {
        // Return minimal valid cache file header
        // Format: compression (1 byte) + length (4 bytes) + data

        let mut data = Vec::with_capacity(5);

        // No compression
        data.push(0);

        // Length (0 bytes of data)
        data.extend_from_slice(&0u32.to_be_bytes());

        data
    }

    /// Get the cache path
    pub fn path(&self) -> &Path {
        &self.path
    }

    /// Get the number of indices
    pub fn index_count(&self) -> usize {
        let loaded_count = *self.index_count.read().unwrap();
        if loaded_count > 0 {
            loaded_count
        } else {
            INDEX_COUNT
        }
    }
}

impl std::fmt::Debug for CacheStore {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("CacheStore")
            .field("path", &self.path)
            .field("loaded", &self.is_loaded())
            .field("index_count", &self.index_count())
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::env::temp_dir;

    #[test]
    fn test_cache_store_creation() {
        let path = temp_dir().join("rustscape_cache_test");
        let store = CacheStore::new(&path).unwrap();

        assert_eq!(store.path(), path);
        assert!(!store.is_loaded()); // No actual cache files
    }

    #[test]
    fn test_stub_checksum_table() {
        let path = temp_dir().join("rustscape_cache_test2");
        let store = CacheStore::new(&path).unwrap();

        let table = store.get_checksum_table().unwrap();
        assert!(!table.is_empty());
        assert_eq!(table.len(), INDEX_COUNT * 8);
    }

    #[test]
    fn test_stub_reference_table() {
        let path = temp_dir().join("rustscape_cache_test3");
        let store = CacheStore::new(&path).unwrap();

        let table = store.get_reference_table(0).unwrap();
        assert!(!table.is_empty());
        // Should have: compression(1) + length(4) + format(1) + flags(1) + count(2) = 9 bytes
        assert_eq!(table.len(), 9);
    }

    #[test]
    fn test_stub_file() {
        let path = temp_dir().join("rustscape_cache_test4");
        let store = CacheStore::new(&path).unwrap();

        let file = store.get_file(0, 1).unwrap();
        assert!(!file.is_empty());
        // Should have: compression(1) + length(4) = 5 bytes
        assert_eq!(file.len(), 5);
    }

    #[test]
    fn test_index_count() {
        let path = temp_dir().join("rustscape_cache_test5");
        let store = CacheStore::new(&path).unwrap();

        assert_eq!(store.index_count(), INDEX_COUNT);
    }

    #[test]
    fn test_compression_type() {
        assert_eq!(CompressionType::from_u8(0), Some(CompressionType::None));
        assert_eq!(CompressionType::from_u8(1), Some(CompressionType::Bzip2));
        assert_eq!(CompressionType::from_u8(2), Some(CompressionType::Gzip));
        assert_eq!(CompressionType::from_u8(3), Some(CompressionType::Lzma));
        assert_eq!(CompressionType::from_u8(4), None);
    }
}
