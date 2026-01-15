//! Sprite Extraction CLI Tool
//!
//! Extracts sprites from the game cache and exports them as PNG files
//! for use in the web client.
//!
//! Usage:
//!   extract_sprites --cache <path> --output <path> [--index <id>]
//!
//! Examples:
//!   extract_sprites --cache ./cache --output ./assets/sprites
//!   extract_sprites --cache ./cache --output ./assets/sprites --index 8
//!   extract_sprites --cache ./cache --output ./assets/sprites --parallel

use std::path::PathBuf;
use std::time::Instant;

use rayon::prelude::*;
use tracing::{error, info, trace, warn, Level};
use tracing_subscriber::FmtSubscriber;

// Import from the main crate
use rustscape_server::cache::sprites::{
    ArchiveExtractionJob, ImageFormat, SpriteDecoder, SpriteExporter, SpriteManifest,
    SpriteSheetAtlas, SpriteSheetConfig, SpriteSheetGenerator, EXTRA_SPRITE_INDEX, SPRITE_INDEX,
    TEXTURE_INDEX,
};
use rustscape_server::cache::CacheStore;

/// CLI arguments
struct Args {
    /// Path to the cache directory
    cache_path: PathBuf,
    /// Output directory for extracted sprites
    output_path: PathBuf,
    /// Specific index to extract (if not specified, extracts all sprite indices)
    index: Option<u8>,
    /// Verbose output
    verbose: bool,
    /// Use parallel extraction (recommended for multi-core systems)
    parallel: bool,
    /// Number of threads to use (0 = auto-detect)
    threads: usize,
    /// Output image format (png or qoi)
    format: ImageFormat,
    /// Generate sprite sheets (texture atlases) instead of individual files
    atlas: bool,
    /// Maximum sprite sheet size (width and height)
    atlas_size: u32,
}

fn parse_args() -> Result<Args, String> {
    let args: Vec<String> = std::env::args().collect();

    let mut cache_path: Option<PathBuf> = None;
    let mut output_path: Option<PathBuf> = None;
    let mut index: Option<u8> = None;
    let mut verbose = false;
    let mut parallel = true; // Default to parallel for best performance
    let mut threads: usize = 0; // 0 = auto-detect
    let mut format = ImageFormat::Png; // Default to PNG for compatibility
    let mut atlas = false;
    let mut atlas_size: u32 = 2048;

    let mut i = 1;
    while i < args.len() {
        match args[i].as_str() {
            "--cache" | "-c" => {
                i += 1;
                if i >= args.len() {
                    return Err("Missing value for --cache".to_string());
                }
                cache_path = Some(PathBuf::from(&args[i]));
            }
            "--output" | "-o" => {
                i += 1;
                if i >= args.len() {
                    return Err("Missing value for --output".to_string());
                }
                output_path = Some(PathBuf::from(&args[i]));
            }
            "--index" | "-i" => {
                i += 1;
                if i >= args.len() {
                    return Err("Missing value for --index".to_string());
                }
                index = Some(
                    args[i]
                        .parse()
                        .map_err(|_| format!("Invalid index value: {}", args[i]))?,
                );
            }
            "--verbose" | "-v" => {
                verbose = true;
            }
            "--parallel" | "-p" => {
                parallel = true;
            }
            "--sequential" | "-s" => {
                parallel = false;
            }
            "--threads" | "-t" => {
                i += 1;
                if i >= args.len() {
                    return Err("Missing value for --threads".to_string());
                }
                threads = args[i]
                    .parse()
                    .map_err(|_| format!("Invalid threads value: {}", args[i]))?;
            }
            "--format" | "-f" => {
                i += 1;
                if i >= args.len() {
                    return Err("Missing value for --format".to_string());
                }
                format = ImageFormat::from_str(&args[i])
                    .ok_or_else(|| format!("Invalid format '{}'. Use 'png' or 'qoi'", args[i]))?;
            }
            "--qoi" => {
                format = ImageFormat::Qoi;
            }
            "--png" => {
                format = ImageFormat::Png;
            }
            "--atlas" | "-a" => {
                atlas = true;
            }
            "--atlas-size" => {
                i += 1;
                if i >= args.len() {
                    return Err("Missing value for --atlas-size".to_string());
                }
                atlas_size = args[i]
                    .parse()
                    .map_err(|_| format!("Invalid atlas size: {}", args[i]))?;
            }
            "--help" | "-h" => {
                print_help();
                std::process::exit(0);
            }
            arg => {
                return Err(format!("Unknown argument: {}", arg));
            }
        }
        i += 1;
    }

    let cache_path = cache_path.ok_or("Missing required argument: --cache")?;
    let output_path = output_path.ok_or("Missing required argument: --output")?;

    Ok(Args {
        cache_path,
        output_path,
        index,
        verbose,
        parallel,
        threads,
        format,
        atlas,
        atlas_size,
    })
}

fn print_help() {
    println!(
        r#"
Sprite Extraction Tool - Rustscape

Extracts sprites from the game cache and exports them as PNG files.
Uses parallel processing by default for maximum performance.

USAGE:
    extract_sprites --cache <PATH> --output <PATH> [OPTIONS]

REQUIRED:
    -c, --cache <PATH>     Path to the game cache directory
    -o, --output <PATH>    Output directory for extracted sprites

OPTIONS:
    -i, --index <ID>       Extract only a specific index (8=sprites, 32=textures)
    -f, --format <FMT>     Output format: 'png' (default) or 'qoi' (faster)
        --png              Use PNG format (default, best compatibility)
        --qoi              Use QOI format (3-4x faster encoding, slightly larger)
    -a, --atlas            Generate sprite sheets (texture atlases)
        --atlas-size <N>   Maximum atlas size in pixels (default: 2048)
    -p, --parallel         Use parallel extraction (default, recommended)
    -s, --sequential       Use sequential extraction (slower, for debugging)
    -t, --threads <N>      Number of threads to use (0 = auto-detect, default)
    -v, --verbose          Enable verbose output
    -h, --help             Print this help message

EXAMPLES:
    # Extract all sprites as PNG (default)
    extract_sprites --cache ./cache --output ./assets/sprites

    # Extract as QOI for faster encoding
    extract_sprites --cache ./cache --output ./assets/sprites --qoi

    # Generate sprite sheets (texture atlases)
    extract_sprites --cache ./cache --output ./assets/sprites --atlas

    # Generate 4096x4096 sprite sheets in QOI format
    extract_sprites --cache ./cache --output ./assets/sprites --atlas --atlas-size 4096 --qoi

    # Extract with specific thread count
    extract_sprites --cache ./cache --output ./assets/sprites --threads 8

    # Extract only UI sprites (index 8)
    extract_sprites --cache ./cache --output ./assets/sprites --index 8

    # Extract textures (index 32)
    extract_sprites --cache ./cache --output ./assets/sprites --index 32

    # Sequential extraction for debugging
    extract_sprites --cache ./cache --output ./assets/sprites --sequential

CACHE INDICES:
    8  - UI Sprites (buttons, icons, interface elements)
    32 - Textures (ground textures, object textures)
    34 - Additional sprites (varies by revision)

FORMATS:
    png - Universal compatibility, best compression, slower encoding
    qoi - 3-4x faster encoding, lossless, slightly larger files (~20-30%)

SPRITE SHEETS:
    Use --atlas to combine sprites into texture atlases (sprite sheets).
    Benefits: fewer HTTP requests, fewer GPU texture switches, better batching.
    Each atlas is a single image with a JSON manifest for sprite locations.

PERFORMANCE:
    Parallel extraction uses all available CPU cores by default.
    On a typical 8-core system, expect 4-8x speedup over sequential.
    QOI format provides additional 3-4x speedup over PNG encoding.
"#
    );
}

fn main() {
    // Parse arguments
    let args = match parse_args() {
        Ok(args) => args,
        Err(e) => {
            eprintln!("Error: {}", e);
            eprintln!("Use --help for usage information");
            std::process::exit(1);
        }
    };

    // Initialize logging
    let log_level = if args.verbose {
        Level::DEBUG
    } else {
        Level::INFO
    };

    let subscriber = FmtSubscriber::builder()
        .with_max_level(log_level)
        .with_target(false)
        .with_thread_ids(false)
        .with_file(false)
        .with_line_number(false)
        .finish();

    tracing::subscriber::set_global_default(subscriber).expect("Failed to set subscriber");

    // Run extraction
    if let Err(e) = run_extraction(&args) {
        error!("Extraction failed: {}", e);
        std::process::exit(1);
    }
}

fn run_extraction(args: &Args) -> Result<(), Box<dyn std::error::Error>> {
    let start_time = Instant::now();

    // Configure thread pool if specified
    if args.threads > 0 {
        rayon::ThreadPoolBuilder::new()
            .num_threads(args.threads)
            .build_global()
            .unwrap_or_else(|e| {
                warn!("Failed to set thread count, using default: {}", e);
            });
    }

    let num_threads = rayon::current_num_threads();

    info!("Sprite Extraction Tool");
    info!("======================");
    info!("Cache path: {:?}", args.cache_path);
    info!("Output path: {:?}", args.output_path);
    info!(
        "Mode: {} ({} threads), Format: {}, Atlas: {}",
        if args.parallel {
            "parallel"
        } else {
            "sequential"
        },
        num_threads,
        args.format.extension().to_uppercase(),
        if args.atlas {
            format!("yes ({}x{})", args.atlas_size, args.atlas_size)
        } else {
            "no".to_string()
        }
    );

    // Check if cache directory exists
    if !args.cache_path.exists() {
        return Err(format!("Cache directory not found: {:?}", args.cache_path).into());
    }

    // Load the cache
    info!("Loading cache...");
    let cache = CacheStore::new(&args.cache_path)?;

    if !cache.is_loaded() {
        warn!("Cache could not be loaded from disk");
        warn!("Make sure the cache files exist:");
        warn!("  - main_file_cache.dat2");
        warn!("  - main_file_cache.idx0 through idx255");
        return Err("Cache not loaded".into());
    }

    // Determine which indices to extract
    let indices: Vec<u8> = if let Some(index) = args.index {
        vec![index]
    } else {
        vec![SPRITE_INDEX, TEXTURE_INDEX, EXTRA_SPRITE_INDEX]
    };

    // Create exporter with specified format
    let exporter = SpriteExporter::with_format(&args.output_path, args.format);

    // Create sprite sheet generator if atlas mode is enabled
    let sheet_generator = if args.atlas {
        Some(SpriteSheetGenerator::with_config(SpriteSheetConfig {
            max_width: args.atlas_size,
            max_height: args.atlas_size,
            padding: 1,
            format: args.format,
        }))
    } else {
        None
    };

    // Create manifests for each index
    let mut manifests: Vec<(String, SpriteManifest)> = Vec::new();

    // Track sprite sheets generated (for atlas mode)
    let mut all_atlases: Vec<SpriteSheetAtlas> = Vec::new();

    for &index in &indices {
        let index_name = match index {
            SPRITE_INDEX => "sprites",
            TEXTURE_INDEX => "textures",
            EXTRA_SPRITE_INDEX => "extra",
            _ => "unknown",
        };

        info!("========================================");
        info!("Extracting index {} ({})...", index, index_name);
        info!("========================================");

        let mut manifest = SpriteManifest::new(index_name);

        // Get reference table for this index
        if let Some(ref_table) = cache.get_parsed_reference_table(index) {
            let archive_count = ref_table.archives.len();
            info!("Found {} archives in index {}", archive_count, index);

            if args.parallel {
                // === PARALLEL EXTRACTION ===
                info!("Using parallel extraction...");
                let index_start = Instant::now();

                // Collect all archive data first (decompressed for sprite parsing)
                let jobs: Vec<ArchiveExtractionJob> = ref_table
                    .archives
                    .iter()
                    .filter_map(|archive_info| {
                        let archive_id = archive_info.id;
                        match cache.get_decompressed_file(index, archive_id) {
                            Ok(data) if !data.is_empty() => {
                                Some(ArchiveExtractionJob { archive_id, data })
                            }
                            _ => None,
                        }
                    })
                    .collect();

                info!("  Loaded {} non-empty archives", jobs.len());

                // Decode all sprites in parallel first (for manifest)
                let all_sprites: Vec<_> = jobs
                    .par_iter()
                    .flat_map(|job| {
                        SpriteDecoder::decode(job.archive_id, &job.data).unwrap_or_default()
                    })
                    .collect();

                info!("  Decoded {} sprites", all_sprites.len());

                // Add to manifest (sequential, fast)
                for sprite in &all_sprites {
                    manifest.add_sprite_with_format(sprite, index_name, args.format);
                }

                // Export sprites
                if let Some(ref generator) = sheet_generator {
                    // Generate sprite sheets
                    let sheets = generator.generate(&all_sprites, index_name);
                    info!("  Generated {} sprite sheets", sheets.len());

                    // Save sprite sheets
                    let atlas_dir = args.output_path.join(index_name);
                    let _ = std::fs::create_dir_all(&atlas_dir);

                    for (sheet_data, atlas) in sheets {
                        let sheet_path = atlas_dir.join(&atlas.image);
                        if let Err(e) = std::fs::write(&sheet_path, &sheet_data) {
                            warn!("Failed to write sprite sheet: {}", e);
                        }

                        // Save atlas JSON
                        let atlas_json_path = atlas_dir.join(format!("{}.json", atlas.name));
                        if let Ok(json) = serde_json::to_string_pretty(&atlas) {
                            let _ = std::fs::write(&atlas_json_path, json);
                        }

                        // Track for summary
                        all_atlases.push(atlas);
                    }
                } else {
                    // Export all sprites in parallel as individual files
                    all_sprites.par_iter().for_each(|sprite| {
                        let _ = exporter.export_sprite(sprite, index_name);
                    });
                }

                let index_elapsed = index_start.elapsed();
                info!(
                    "  Parallel extraction completed in {:.2}s",
                    index_elapsed.as_secs_f64()
                );
            } else {
                // === SEQUENTIAL EXTRACTION ===
                for (idx, archive_info) in ref_table.archives.iter().enumerate() {
                    let archive_id = archive_info.id;

                    // Progress logging - every 50 archives for more visibility
                    if idx % 50 == 0 {
                        info!(
                            "  [{}/{}] Processing archive {} ({}%)",
                            idx,
                            archive_count,
                            archive_id,
                            (idx * 100) / archive_count.max(1)
                        );
                        // Flush stdout to ensure progress is visible in Docker builds
                        use std::io::Write;
                        let _ = std::io::stdout().flush();
                    }

                    // Get the decompressed archive data
                    match cache.get_decompressed_file(index, archive_id) {
                        Ok(data) => {
                            if data.is_empty() {
                                if args.verbose {
                                    trace!("Archive {} in index {} is empty", archive_id, index);
                                }
                                continue;
                            }

                            if args.verbose && idx % 200 == 0 {
                                info!("    Decoding archive {} ({} bytes)", archive_id, data.len());
                            }

                            // Decode sprites from the archive
                            match SpriteDecoder::decode(archive_id, &data) {
                                Ok(sprites) => {
                                    let sprite_count = sprites.len();
                                    for sprite in &sprites {
                                        manifest.add_sprite_with_format(
                                            sprite,
                                            index_name,
                                            args.format,
                                        );
                                        if sheet_generator.is_none() {
                                            if let Err(e) =
                                                exporter.export_sprite(sprite, index_name)
                                            {
                                                if args.verbose {
                                                    trace!(
                                                        "Failed to export sprite {}: {}",
                                                        sprite.id,
                                                        e
                                                    );
                                                }
                                            }
                                        }
                                    }
                                    if args.verbose && sprite_count > 0 && idx % 200 == 0 {
                                        info!(
                                            "    Extracted {} sprites from archive {}",
                                            sprite_count, archive_id
                                        );
                                    }
                                }
                                Err(e) => {
                                    if args.verbose {
                                        trace!(
                                            "Failed to decode archive {} in index {}: {}",
                                            archive_id,
                                            index,
                                            e
                                        );
                                    }
                                }
                            }
                        }
                        Err(e) => {
                            if args.verbose {
                                trace!(
                                    "Failed to read archive {} in index {}: {}",
                                    archive_id,
                                    index,
                                    e
                                );
                            }
                        }
                    }
                }
            }
        } else {
            warn!("No reference table found for index {}", index);
        }

        info!(
            "  Completed index {} - extracted {} sprites so far",
            index,
            exporter.exported_count()
        );
        manifests.push((index_name.to_string(), manifest));
    }

    info!("");
    info!("========================================");
    info!("Saving manifests...");
    info!("========================================");
    for (name, manifest) in &manifests {
        let manifest_path = args.output_path.join(format!("{}_manifest.json", name));
        if let Err(e) = manifest.save(&manifest_path) {
            warn!("Failed to save manifest for {}: {}", name, e);
        } else {
            info!(
                "Saved manifest: {:?} ({} sprites)",
                manifest_path, manifest.count
            );
        }
    }

    // Print summary
    let elapsed = start_time.elapsed();
    info!("");
    info!("Extraction Complete!");
    info!("====================");

    if args.atlas {
        // Atlas mode summary
        let total_sprites_in_sheets: usize = all_atlases.iter().map(|a| a.sprites.len()).sum();
        info!(
            "Generated: {} sprite sheet(s) containing {} sprites",
            all_atlases.len(),
            total_sprites_in_sheets
        );
        for atlas in &all_atlases {
            info!(
                "  - {}: {}x{} ({} sprites)",
                atlas.image,
                atlas.width,
                atlas.height,
                atlas.sprites.len()
            );
        }
    } else {
        // Individual file mode summary
        info!("Exported: {} sprites", exporter.exported_count());
        info!("Failed: {} sprites", exporter.failed_count());
    }

    info!("Time: {:.2}s", elapsed.as_secs_f64());
    info!("Output: {:?}", exporter.output_dir());

    Ok(())
}
