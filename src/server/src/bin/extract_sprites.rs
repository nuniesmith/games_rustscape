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

use std::path::PathBuf;
use std::time::Instant;

use tracing::{error, info, warn, Level};
use tracing_subscriber::FmtSubscriber;

// Import from the main crate
use rustscape_server::cache::sprites::{
    SpriteDecoder, SpriteExporter, SpriteManifest, EXTRA_SPRITE_INDEX, SPRITE_INDEX, TEXTURE_INDEX,
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
}

fn parse_args() -> Result<Args, String> {
    let args: Vec<String> = std::env::args().collect();

    let mut cache_path: Option<PathBuf> = None;
    let mut output_path: Option<PathBuf> = None;
    let mut index: Option<u8> = None;
    let mut verbose = false;

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
    })
}

fn print_help() {
    println!(
        r#"
Sprite Extraction Tool - Rustscape

Extracts sprites from the game cache and exports them as PNG files.

USAGE:
    extract_sprites --cache <PATH> --output <PATH> [OPTIONS]

REQUIRED:
    -c, --cache <PATH>     Path to the game cache directory
    -o, --output <PATH>    Output directory for extracted sprites

OPTIONS:
    -i, --index <ID>       Extract only a specific index (8=sprites, 32=textures)
    -v, --verbose          Enable verbose output
    -h, --help             Print this help message

EXAMPLES:
    # Extract all sprites
    extract_sprites --cache ./cache --output ./assets/sprites

    # Extract only UI sprites (index 8)
    extract_sprites --cache ./cache --output ./assets/sprites --index 8

    # Extract textures (index 32)
    extract_sprites --cache ./cache --output ./assets/sprites --index 32

CACHE INDICES:
    8  - UI Sprites (buttons, icons, interface elements)
    32 - Textures (ground textures, object textures)
    34 - Additional sprites (varies by revision)
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

    info!("Sprite Extraction Tool");
    info!("======================");
    info!("Cache path: {:?}", args.cache_path);
    info!("Output path: {:?}", args.output_path);

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

    // Create exporter
    let mut exporter = SpriteExporter::new(&args.output_path);

    // Create manifests for each index
    let mut manifests: Vec<(String, SpriteManifest)> = Vec::new();

    for &index in &indices {
        let index_name = match index {
            SPRITE_INDEX => "sprites",
            TEXTURE_INDEX => "textures",
            EXTRA_SPRITE_INDEX => "extra",
            _ => "unknown",
        };

        info!("Extracting index {} ({})...", index, index_name);

        let mut manifest = SpriteManifest::new(index_name);

        // Get reference table for this index
        if let Some(ref_table) = cache.get_parsed_reference_table(index) {
            let archive_count = ref_table.archives.len();
            info!("Found {} archives in index {}", archive_count, index);

            for (idx, archive_info) in ref_table.archives.iter().enumerate() {
                let archive_id = archive_info.id;

                // Progress logging
                if idx > 0 && idx % 100 == 0 {
                    info!(
                        "  Progress: {}/{} archives ({}%)",
                        idx,
                        archive_count,
                        (idx * 100) / archive_count
                    );
                }

                // Get the archive data
                match cache.get_file(index, archive_id) {
                    Ok(data) => {
                        if data.is_empty() {
                            if args.verbose {
                                warn!("Archive {} in index {} is empty", archive_id, index);
                            }
                            continue;
                        }
                        // Decode sprites from the archive
                        match SpriteDecoder::decode(archive_id, &data) {
                            Ok(sprites) => {
                                for sprite in &sprites {
                                    manifest.add_sprite(sprite, index_name);
                                    let _ = exporter.export_sprite(sprite, index_name);
                                }
                            }
                            Err(e) => {
                                if args.verbose {
                                    warn!(
                                        "Failed to decode archive {} in index {}: {}",
                                        archive_id, index, e
                                    );
                                }
                            }
                        }
                    }
                    Err(e) => {
                        if args.verbose {
                            warn!(
                                "Failed to read archive {} in index {}: {}",
                                archive_id, index, e
                            );
                        }
                    }
                }
            }
        } else {
            warn!("No reference table found for index {}", index);
        }

        manifests.push((index_name.to_string(), manifest));
    }

    // Save manifests
    info!("Saving manifests...");
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
    info!("Exported: {} sprites", exporter.exported_count());
    info!("Failed: {} sprites", exporter.failed_count());
    info!("Time: {:.2}s", elapsed.as_secs_f64());
    info!("Output: {:?}", exporter.output_dir());

    Ok(())
}
