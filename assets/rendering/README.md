# Rustscape Rendering Assets

This directory contains pre-extracted rendering assets for the Rustscape game client.

## Sprites

The `sprites/` directory contains **pre-extracted PNG sprites** from the RuneScape revision 530 cache (OpenRS2 Archive #254, ~2009 HD era).

### Usage

Sprites can be loaded directly by their ID:

```kotlin
// Load sprite by ID
val sprite = loadSprite("sprites/123.png")

// Some sprites have multiple frames (e.g., animations)
val frame0 = loadSprite("sprites/318_0.png")
val frame1 = loadSprite("sprites/318_1.png")
```

### Sprite Naming Convention

- `{id}.png` - Single-frame sprite with the given archive ID
- `{id}_{frame}.png` - Multi-frame sprite (animations, sprite sheets)

### Sprite Count

- **~3,800+ PNG files** covering UI elements, icons, cursors, buttons, and interface graphics
- All sprites are pre-extracted and ready to use - no runtime extraction needed

## Directory Structure

```
rendering/
├── README.md          # This file
└── sprites/           # Pre-extracted PNG sprites
    ├── 0.png
    ├── 1.png
    ├── ...
    └── 3796.png
```

## Common Sprite IDs

| ID Range | Contents |
|----------|----------|
| 0-100 | UI elements, buttons |
| 100-300 | Interface icons |
| 300-500 | Skill icons, cursors |
| 500-1000 | Equipment slots, inventory |
| 1000+ | Various UI graphics |

## Source

- **Archive**: [OpenRS2 #254](https://archive.openrs2.org/caches/runescape/254)
- **Revision**: 530
- **Era**: ~2009 (HD update era)
- **Format**: PNG (converted from Jagex sprite format)

## Legal Notice

These assets are from the original RuneScape game by Jagex Ltd. They are provided for educational and preservation purposes as part of the OpenRS2 archive project.