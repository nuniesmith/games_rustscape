#!/bin/bash
#
# Sprite Extraction Script for Rustscape
#
# This script extracts sprites from the game cache and prepares them
# for the web client. It supports multiple output formats and modes.
#
# Usage:
#   ./scripts/extract-sprites.sh [options]
#
# Options:
#   --format <png|qoi>   Output format (default: png)
#   --atlas              Generate sprite sheets instead of individual files
#   --atlas-size <N>     Maximum atlas size in pixels (default: 2048)
#   --output <path>      Output directory (default: src/clients/web/public/sprites)
#   --cache <path>       Cache directory (default: cache/cache)
#   --clean              Remove existing sprites before extraction
#   --help               Show this help message
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
FORMAT="png"
ATLAS=false
ATLAS_SIZE=2048
OUTPUT_DIR=""
CACHE_DIR=""
CLEAN=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --format)
            FORMAT="$2"
            shift 2
            ;;
        --atlas)
            ATLAS=true
            shift
            ;;
        --atlas-size)
            ATLAS_SIZE="$2"
            shift 2
            ;;
        --output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --cache)
            CACHE_DIR="$2"
            shift 2
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        --help)
            echo "Sprite Extraction Script for Rustscape"
            echo ""
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --format <png|qoi>   Output format (default: png)"
            echo "  --atlas              Generate sprite sheets instead of individual files"
            echo "  --atlas-size <N>     Maximum atlas size in pixels (default: 2048)"
            echo "  --output <path>      Output directory (default: src/clients/web/public/sprites)"
            echo "  --cache <path>       Cache directory (default: cache/cache)"
            echo "  --clean              Remove existing sprites before extraction"
            echo "  --help               Show this help message"
            echo ""
            echo "Examples:"
            echo "  # Extract sprites as PNG (default)"
            echo "  $0"
            echo ""
            echo "  # Extract as QOI format (faster)"
            echo "  $0 --format qoi"
            echo ""
            echo "  # Generate sprite sheets"
            echo "  $0 --atlas"
            echo ""
            echo "  # Generate 4096x4096 sprite sheets in QOI format"
            echo "  $0 --atlas --atlas-size 4096 --format qoi"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Set default paths if not specified
if [[ -z "$OUTPUT_DIR" ]]; then
    OUTPUT_DIR="$PROJECT_ROOT/src/clients/web/public/sprites"
fi

if [[ -z "$CACHE_DIR" ]]; then
    CACHE_DIR="$PROJECT_ROOT/cache/cache"
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Rustscape Sprite Extraction${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "Format:     ${GREEN}$FORMAT${NC}"
echo -e "Atlas mode: ${GREEN}$ATLAS${NC}"
if [[ "$ATLAS" == true ]]; then
    echo -e "Atlas size: ${GREEN}${ATLAS_SIZE}x${ATLAS_SIZE}${NC}"
fi
echo -e "Output:     ${GREEN}$OUTPUT_DIR${NC}"
echo -e "Cache:      ${GREEN}$CACHE_DIR${NC}"
echo ""

# Check if cache exists
if [[ ! -d "$CACHE_DIR" ]]; then
    echo -e "${YELLOW}Cache directory not found: $CACHE_DIR${NC}"
    echo -e "${YELLOW}Checking for disk.zip to extract...${NC}"

    DISK_ZIP="$PROJECT_ROOT/cache/disk.zip"
    if [[ -f "$DISK_ZIP" ]]; then
        echo -e "${GREEN}Found disk.zip, extracting...${NC}"
        cd "$PROJECT_ROOT/cache"
        unzip -o disk.zip
        cd "$PROJECT_ROOT"
    else
        echo -e "${RED}Error: Neither cache directory nor disk.zip found${NC}"
        echo -e "${RED}Please download the cache from OpenRS2:${NC}"
        echo -e "${RED}  curl -L -o cache/disk.zip 'https://archive.openrs2.org/caches/runescape/254/disk.zip'${NC}"
        exit 1
    fi
fi

# Check if extractor binary exists
EXTRACTOR="$PROJECT_ROOT/src/server/target/release/extract-sprites"
if [[ ! -f "$EXTRACTOR" ]]; then
    echo -e "${YELLOW}Extractor binary not found, building...${NC}"
    cd "$PROJECT_ROOT/src/server"
    cargo build --release --bin extract-sprites
    cd "$PROJECT_ROOT"
fi

# Clean existing sprites if requested
if [[ "$CLEAN" == true ]]; then
    echo -e "${YELLOW}Cleaning existing sprites...${NC}"
    rm -rf "$OUTPUT_DIR"
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Build extraction command
CMD="$EXTRACTOR --cache $CACHE_DIR --output $OUTPUT_DIR --parallel"

# Add format flag
if [[ "$FORMAT" == "qoi" ]]; then
    CMD="$CMD --qoi"
else
    CMD="$CMD --png"
fi

# Add atlas flags
if [[ "$ATLAS" == true ]]; then
    CMD="$CMD --atlas --atlas-size $ATLAS_SIZE"
fi

# Run extraction
echo -e "${GREEN}Running extraction...${NC}"
echo -e "${BLUE}$ $CMD${NC}"
echo ""

$CMD

# Show results
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Extraction Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Count files
if [[ "$ATLAS" == true ]]; then
    ATLAS_COUNT=$(find "$OUTPUT_DIR" -name "*.json" -path "*/sprites/*" | wc -l)
    echo -e "Generated ${GREEN}$ATLAS_COUNT${NC} sprite sheet(s)"
else
    FILE_COUNT=$(find "$OUTPUT_DIR" -name "*.$FORMAT" | wc -l)
    echo -e "Generated ${GREEN}$FILE_COUNT${NC} sprite files"
fi

# Show total size
TOTAL_SIZE=$(du -sh "$OUTPUT_DIR" | cut -f1)
echo -e "Total size: ${GREEN}$TOTAL_SIZE${NC}"
echo ""

# Suggest next steps
echo -e "${BLUE}Next steps:${NC}"
echo "  1. Review the extracted sprites in: $OUTPUT_DIR"
echo "  2. To commit sprites to the repo:"
echo "     git add $OUTPUT_DIR"
echo "     git commit -m 'chore: pre-extract sprites for web client'"
echo ""
