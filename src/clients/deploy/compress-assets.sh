#!/bin/bash
#
# Rustscape WASM Client - Asset Compression Script
#
# This script compresses the production webpack output files with both
# gzip and brotli for optimal transfer sizes when served by nginx.
#
# Usage:
#   ./compress-assets.sh [output_dir]
#
# Default output_dir is: ../composeApp/build/dist/wasmJs/productionExecutable
#
# Requirements:
#   - gzip (usually pre-installed)
#   - brotli (install via: apt install brotli / brew install brotli)
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default output directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_OUTPUT_DIR="${SCRIPT_DIR}/../composeApp/build/dist/wasmJs/productionExecutable"
OUTPUT_DIR="${1:-$DEFAULT_OUTPUT_DIR}"

echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║       Rustscape WASM Client - Asset Compression           ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if output directory exists
if [ ! -d "$OUTPUT_DIR" ]; then
    echo -e "${RED}Error: Output directory does not exist: ${OUTPUT_DIR}${NC}"
    echo -e "${YELLOW}Have you run the production build?${NC}"
    echo -e "  ./gradlew wasmJsBrowserProductionWebpack"
    exit 1
fi

echo -e "${GREEN}Output directory:${NC} $OUTPUT_DIR"
echo ""

# Check for brotli
HAVE_BROTLI=false
if command -v brotli &> /dev/null; then
    HAVE_BROTLI=true
    echo -e "${GREEN}✓ brotli found${NC}"
else
    echo -e "${YELLOW}⚠ brotli not found - skipping .br compression${NC}"
    echo -e "  Install with: ${CYAN}apt install brotli${NC} or ${CYAN}brew install brotli${NC}"
fi

# Check for gzip
if ! command -v gzip &> /dev/null; then
    echo -e "${RED}Error: gzip not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓ gzip found${NC}"
echo ""

# Function to get file size in human-readable format
get_size() {
    if [ -f "$1" ]; then
        if [[ "$OSTYPE" == "darwin"* ]]; then
            stat -f%z "$1" | awk '{ split( "B KB MB GB" , v ); s=1; while( $1>1024 ){ $1/=1024; s++ } printf "%.2f %s", $1, v[s] }'
        else
            stat --printf="%s" "$1" | awk '{ split( "B KB MB GB" , v ); s=1; while( $1>1024 ){ $1/=1024; s++ } printf "%.2f %s", $1, v[s] }'
        fi
    else
        echo "N/A"
    fi
}

# Function to compress a single file
compress_file() {
    local file="$1"
    local filename=$(basename "$file")
    local original_size=$(get_size "$file")

    echo -e "${CYAN}Compressing:${NC} $filename (${original_size})"

    # Gzip compression (level 9 - best compression)
    if [ ! -f "${file}.gz" ] || [ "$file" -nt "${file}.gz" ]; then
        gzip -9 -k -f "$file"
        local gz_size=$(get_size "${file}.gz")
        echo -e "  ${GREEN}→ .gz${NC} (${gz_size})"
    else
        echo -e "  ${YELLOW}→ .gz${NC} (already exists, skipped)"
    fi

    # Brotli compression (level 11 - best compression)
    if [ "$HAVE_BROTLI" = true ]; then
        if [ ! -f "${file}.br" ] || [ "$file" -nt "${file}.br" ]; then
            brotli -9 -k -f "$file"
            local br_size=$(get_size "${file}.br")
            echo -e "  ${GREEN}→ .br${NC} (${br_size})"
        else
            echo -e "  ${YELLOW}→ .br${NC} (already exists, skipped)"
        fi
    fi
}

# Count files to process
WASM_FILES=$(find "$OUTPUT_DIR" -name "*.wasm" -type f 2>/dev/null | wc -l | tr -d ' ')
JS_FILES=$(find "$OUTPUT_DIR" -name "*.js" -type f ! -name "*.gz" ! -name "*.br" 2>/dev/null | wc -l | tr -d ' ')
HTML_FILES=$(find "$OUTPUT_DIR" -name "*.html" -type f 2>/dev/null | wc -l | tr -d ' ')
CSS_FILES=$(find "$OUTPUT_DIR" -name "*.css" -type f 2>/dev/null | wc -l | tr -d ' ')

TOTAL_FILES=$((WASM_FILES + JS_FILES + HTML_FILES + CSS_FILES))

echo -e "${GREEN}Files to compress:${NC}"
echo -e "  WASM: ${WASM_FILES}"
echo -e "  JS:   ${JS_FILES}"
echo -e "  HTML: ${HTML_FILES}"
echo -e "  CSS:  ${CSS_FILES}"
echo -e "  ────────────"
echo -e "  Total: ${TOTAL_FILES}"
echo ""

if [ "$TOTAL_FILES" -eq 0 ]; then
    echo -e "${YELLOW}No files found to compress!${NC}"
    exit 0
fi

# Process WASM files (most important for size reduction)
echo -e "${CYAN}═══ WASM Files ═══${NC}"
find "$OUTPUT_DIR" -name "*.wasm" -type f | while read -r file; do
    compress_file "$file"
done
echo ""

# Process JS files
echo -e "${CYAN}═══ JavaScript Files ═══${NC}"
find "$OUTPUT_DIR" -name "*.js" -type f ! -name "*.gz" ! -name "*.br" | while read -r file; do
    compress_file "$file"
done
echo ""

# Process HTML files
if [ "$HTML_FILES" -gt 0 ]; then
    echo -e "${CYAN}═══ HTML Files ═══${NC}"
    find "$OUTPUT_DIR" -name "*.html" -type f | while read -r file; do
        compress_file "$file"
    done
    echo ""
fi

# Process CSS files
if [ "$CSS_FILES" -gt 0 ]; then
    echo -e "${CYAN}═══ CSS Files ═══${NC}"
    find "$OUTPUT_DIR" -name "*.css" -type f | while read -r file; do
        compress_file "$file"
    done
    echo ""
fi

# Summary
echo -e "${CYAN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║                     Compression Summary                    ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Calculate total sizes
ORIGINAL_SIZE=0
GZ_SIZE=0
BR_SIZE=0

for ext in wasm js html css; do
    while IFS= read -r -d '' file; do
        if [ -f "$file" ]; then
            if [[ "$OSTYPE" == "darwin"* ]]; then
                size=$(stat -f%z "$file" 2>/dev/null || echo 0)
            else
                size=$(stat --printf="%s" "$file" 2>/dev/null || echo 0)
            fi
            ORIGINAL_SIZE=$((ORIGINAL_SIZE + size))
        fi

        if [ -f "${file}.gz" ]; then
            if [[ "$OSTYPE" == "darwin"* ]]; then
                size=$(stat -f%z "${file}.gz" 2>/dev/null || echo 0)
            else
                size=$(stat --printf="%s" "${file}.gz" 2>/dev/null || echo 0)
            fi
            GZ_SIZE=$((GZ_SIZE + size))
        fi

        if [ -f "${file}.br" ]; then
            if [[ "$OSTYPE" == "darwin"* ]]; then
                size=$(stat -f%z "${file}.br" 2>/dev/null || echo 0)
            else
                size=$(stat --printf="%s" "${file}.br" 2>/dev/null || echo 0)
            fi
            BR_SIZE=$((BR_SIZE + size))
        fi
    done < <(find "$OUTPUT_DIR" -name "*.${ext}" -type f -print0 2>/dev/null)
done

# Convert to human-readable
format_size() {
    local size=$1
    if [ $size -ge 1073741824 ]; then
        echo "$(echo "scale=2; $size/1073741824" | bc) GB"
    elif [ $size -ge 1048576 ]; then
        echo "$(echo "scale=2; $size/1048576" | bc) MB"
    elif [ $size -ge 1024 ]; then
        echo "$(echo "scale=2; $size/1024" | bc) KB"
    else
        echo "$size B"
    fi
}

echo -e "Original size: ${YELLOW}$(format_size $ORIGINAL_SIZE)${NC}"
if [ $GZ_SIZE -gt 0 ]; then
    GZ_RATIO=$(echo "scale=1; 100 - ($GZ_SIZE * 100 / $ORIGINAL_SIZE)" | bc)
    echo -e "Gzip size:     ${GREEN}$(format_size $GZ_SIZE)${NC} (${GZ_RATIO}% reduction)"
fi
if [ $BR_SIZE -gt 0 ]; then
    BR_RATIO=$(echo "scale=1; 100 - ($BR_SIZE * 100 / $ORIGINAL_SIZE)" | bc)
    echo -e "Brotli size:   ${GREEN}$(format_size $BR_SIZE)${NC} (${BR_RATIO}% reduction)"
fi

echo ""
echo -e "${GREEN}✓ Compression complete!${NC}"
echo ""
echo -e "Next steps:"
echo -e "  1. Copy files to your web server: ${CYAN}rsync -av ${OUTPUT_DIR}/ user@server:/var/www/rustscape/${NC}"
echo -e "  2. Configure nginx with the provided nginx.conf"
echo -e "  3. Reload nginx: ${CYAN}sudo nginx -s reload${NC}"
echo ""
