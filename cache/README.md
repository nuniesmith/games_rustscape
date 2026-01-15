# Rustscape Game Cache

This directory contains the RuneScape revision 530 game cache files used for rendering sprites, textures, maps, and other game assets.

## File Structure

The cache consists of:
- **`main_file_cache.dat2`** - Main data file containing all game assets (~86MB)
- **`main_file_cache.idx0` - `idx255`** - Index files that map to data locations

## Split Files for GitHub

Since GitHub has a 100MB file size limit, the large `main_file_cache.dat2` file is split into 20MB chunks:

```
main_file_cache.dat2.part_aa  (20MB)
main_file_cache.dat2.part_ab  (20MB)
main_file_cache.dat2.part_ac  (20MB)
main_file_cache.dat2.part_ad  (20MB)
main_file_cache.dat2.part_ae  (~5MB)
```

## Assembling the Cache

The cache is automatically assembled when you run any cache-related command:

```bash
./run.sh cache check
# or
./run.sh sprites extract
```

You can also manually assemble it:

```bash
cd cache
./assemble.sh
# or manually:
cat main_file_cache.dat2.part_* > main_file_cache.dat2
```

## Cache Source

This cache was downloaded from the [OpenRS2 Archive](https://archive.openrs2.org/caches/runescape/254), which maintains historical RuneScape cache files.

- **Revision**: 530
- **Era**: ~2009 (HD update era)
- **Size**: ~65MB compressed, ~86MB uncompressed

## Extracting Sprites

Once the cache is assembled, you can extract sprites for the web client:

```bash
./run.sh sprites extract
```

This will:
1. Build the sprite extraction tool
2. Extract UI sprites (index 8)
3. Extract textures (index 32)
4. Save PNG files to `src/clients/web/public/sprites/`

## Cache Contents

| Index | Contents | Description |
|-------|----------|-------------|
| 0 | Animations | Animation sequences |
| 1 | Skeletons | Animation skeletons |
| 2 | Configs | Game configuration data |
| 3 | Interfaces | UI interface definitions |
| 4 | Sound effects | Audio files |
| 5 | Maps | World map data |
| 6 | Music | Background music |
| 7 | Models | 3D models |
| 8 | Sprites | UI sprites and icons |
| 9 | Textures | Ground and object textures |
| 10+ | Various | Other game data |

## Legal Notice

These cache files are from the original RuneScape game by Jagex Ltd. They are provided for educational and preservation purposes as part of the OpenRS2 archive project.