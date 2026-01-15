# Rustscape Game Cache

This directory contains the RuneScape revision 530 game cache files used for rendering sprites, textures, maps, and other game assets.

## Quick Start

The cache is stored as `disk.zip` (66MB) from [OpenRS2 Archive #254](https://archive.openrs2.org/caches/runescape/254).

During Docker build, the cache is automatically extracted and sprites are exported to PNG files.

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

## Extracting Sprites

Sprites are extracted automatically during Docker build. To extract manually:

```bash
# Extract cache files
cd cache
unzip -o disk.zip

# Build and run the sprite extractor
cd ..
cargo build --release --bin extract-sprites
./target/release/extract-sprites \
    --cache ./cache/cache \
    --output ./src/clients/web/public/sprites \
    --verbose
```

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
4. Run extraction: `extract-sprites --cache ./cache --output /sprites`
5. Copy extracted PNGs to nginx static files

## Source

- **Archive**: [OpenRS2 #254](https://archive.openrs2.org/caches/runescape/254)
- **Revision**: 530
- **Era**: ~2009 (HD update era)
- **Format**: Traditional `.dat2/.idx` disk format

## Legal Notice

These cache files are from the original RuneScape game by Jagex Ltd. They are provided for educational and preservation purposes as part of the OpenRS2 archive project.