# Rustscape Game Cache

This directory contains the RuneScape revision 530 game cache files used for rendering sprites, textures, maps, and other game assets.

## Quick Start

The cache is stored as `disk.zip` (66MB) from [OpenRS2 Archive #254](https://archive.openrs2.org/caches/runescape/254).

```bash
# Extract sprites (recommended: use the helper script)
./scripts/extract-sprites.sh

# Or with sprite sheets for better web performance
./scripts/extract-sprites.sh --atlas
```

## Files

| File | Size | Description |
|------|------|-------------|
| `disk.zip` | 66MB | OpenRS2 cache archive (traditional `.dat2/.idx` format) |
| `keys-*.json` | 241KB | XTEA encryption keys for map decryption |

## Cache Contents (inside disk.zip)

The zip contains the traditional Jagex cache format:
- **`main_file_cache.dat2`** - Main data file containing all game assets (~89MB)
- **`main_file_cache.idx0` - `idx27`** - Index files that map to data locations
- **`main_file_cache.idx255`** - Reference index containing metadata

## Cache Indices

| Index | Contents | Groups | Description |
|-------|----------|--------|-------------|
| 0 | Animations | 2,724 | Animation sequences |
| 1 | Skeletons | 2,435 | Animation skeletons |
| 2 | Configs | 20 | Game configuration data |
| 3 | Interfaces | 832 | UI interface definitions |
| 4 | Sound FX | 5,846 | Sound effects |
| 5 | Maps | 3,682 | World map data (XTEA encrypted) |
| 6 | Music | 625 | Background music |
| 7 | Models | 45,468 | 3D models |
| 8 | **Sprites** | 1,707 | UI sprites and icons |
| 9 | Textures | 680 | Ground and object textures |
| 10-27 | Various | - | Other game data |

## Sprite Extraction

### Using the Helper Script (Recommended)

```bash
# Extract as individual PNG files (default)
./scripts/extract-sprites.sh

# Extract as QOI format (faster encoding)
./scripts/extract-sprites.sh --format qoi

# Generate sprite sheets (texture atlases) - best for web performance
./scripts/extract-sprites.sh --atlas

# Generate 4096x4096 sprite sheets in QOI format
./scripts/extract-sprites.sh --atlas --atlas-size 4096 --format qoi

# Clean and re-extract
./scripts/extract-sprites.sh --clean --atlas
```

### Manual Extraction

```bash
# 1. Extract cache files
cd cache
unzip -o disk.zip
cd ..

# 2. Build the sprite extractor
cd src/server
cargo build --release --bin extract-sprites
cd ../..

# 3. Run extraction
./src/server/target/release/extract-sprites \
    --cache ./cache/cache \
    --output ./src/clients/web/public/sprites \
    --parallel

# Additional options:
#   --qoi              Use QOI format (3-4x faster encoding)
#   --atlas            Generate sprite sheets
#   --atlas-size 2048  Maximum atlas dimensions
#   --threads 8        Specify thread count
#   --sequential       Disable parallel processing (for debugging)
```

## Performance Comparison

| Mode | Time | Size | Files | Best For |
|------|------|------|-------|----------|
| PNG Individual | 0.23s | 8.2MB | 2081 | Compatibility |
| QOI Individual | 0.24s | 8.2MB | 2081 | Fast encoding |
| PNG Atlas | 0.13s | 356KB | 2 | **Web production** |
| QOI Atlas | 0.12s | 352KB | 2 | Fastest extraction |

**Sprite sheets are 23x smaller** than individual files and provide:
- Fewer HTTP requests
- Fewer GPU texture switches
- Better batching for WebGL rendering

## Output Formats

### PNG (Default)
- Universal browser compatibility
- Best compression ratio
- Slower encoding

### QOI (Quite OK Image)
- 3-4x faster encoding
- Lossless compression
- Slightly larger files (~20-30%)
- Requires QOI decoder in web client

### Sprite Sheets (Atlas)
- Combines all sprites into texture atlases
- Includes JSON manifest with sprite coordinates
- Dramatically reduces file count and total size
- Optimal for production web deployment

## Downloading Fresh Cache

To download the latest cache from OpenRS2:

```bash
cd cache
curl -L -o disk.zip "https://archive.openrs2.org/caches/runescape/254/disk.zip"
```

## XTEA Keys

The `keys-*.json` file contains XTEA encryption keys for map data (archive 5). These are required to decrypt map terrain and location data. Sprites (archive 8) are not encrypted.

## Docker Build Process

1. Copy `disk.zip` into the build context
2. Extract cache files: `unzip -o disk.zip`
3. Build sprite extractor from Rust source
4. Run extraction: `extract-sprites --cache ./cache --output /sprites --atlas`
5. Copy extracted sprites to nginx static files

## Pre-extracted Sprites

For faster CI builds, you can pre-extract sprites and commit them:

```bash
# Extract using sprite sheets for smallest size
./scripts/extract-sprites.sh --atlas --clean

# Commit the extracted sprites
git add src/clients/web/public/sprites/
git commit -m "chore: pre-extract sprites for web client"
```

This eliminates the need to run extraction during Docker builds.

## Source

- **Archive**: [OpenRS2 #254](https://archive.openrs2.org/caches/runescape/254)
- **Revision**: 530
- **Era**: ~2009 (HD update era)
- **Format**: Traditional `.dat2/.idx` disk format

## Legal Notice

These cache files are from the original RuneScape game by Jagex Ltd. They are provided for educational and preservation purposes as part of the OpenRS2 archive project.