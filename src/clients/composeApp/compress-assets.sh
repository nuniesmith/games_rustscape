#!/bin/bash
# Post-build script to pre-compress WASM and JS assets for production
# These pre-compressed files can be served directly by nginx with gzip_static/brotli_static

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build/dist/wasmJs/productionExecutable"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}╔═══════════════════════════════════════════╗${NC}"
echo -e "${YELLOW}║   Asset Compression for Rustscape WASM    ║${NC}"
echo -e "${YELLOW}╚═══════════════════════════════════════════╝${NC}"
echo ""

# Check if build directory exists
if [ ! -d "$BUILD_DIR" ]; then
    echo -e "${RED}Error: Build directory not found: ${BUILD_DIR}${NC}"
    echo "Run './gradlew wasmJsBrowserProductionWebpack' first."
    exit 1
fi

cd "$BUILD_DIR"

# Function to compress a file and show size reduction
compress_file() {
    local file="$1"
    local original_size
    local gzip_size
    local brotli_size

    if [ ! -f "$file" ]; then
        return
    fi

    original_size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null)

    echo -e "Processing: ${YELLOW}${file}${NC}"
    echo "  Original size: $(numfmt --to=iec-i --suffix=B $original_size 2>/dev/null || echo "${original_size} bytes")"

    # Gzip compression (level 9 for maximum compression)
    if command -v gzip &> /dev/null; then
        gzip -9 -k -f "$file"
        gzip_size=$(stat -f%z "${file}.gz" 2>/dev/null || stat -c%s "${file}.gz" 2>/dev/null)
        local gzip_ratio=$((100 - (gzip_size * 100 / original_size)))
        echo -e "  ${GREEN}Gzip:${NC}     $(numfmt --to=iec-i --suffix=B $gzip_size 2>/dev/null || echo "${gzip_size} bytes") (${gzip_ratio}% reduction)"
    else
        echo -e "  ${RED}gzip not found - skipping${NC}"
    fi

    # Brotli compression (level 11 for maximum compression)
    if command -v brotli &> /dev/null; then
        brotli -9 -k -f "$file"
        brotli_size=$(stat -f%z "${file}.br" 2>/dev/null || stat -c%s "${file}.br" 2>/dev/null)
        local brotli_ratio=$((100 - (brotli_size * 100 / original_size)))
        echo -e "  ${GREEN}Brotli:${NC}   $(numfmt --to=iec-i --suffix=B $brotli_size 2>/dev/null || echo "${brotli_size} bytes") (${brotli_ratio}% reduction)"
    else
        echo -e "  ${YELLOW}brotli not found - install for better compression${NC}"
        echo "  (Install: apt install brotli / brew install brotli)"
    fi

    echo ""
}

echo "Compressing WASM files..."
echo "========================="
for wasm in *.wasm; do
    [ -f "$wasm" ] && compress_file "$wasm"
done

echo "Compressing JavaScript files..."
echo "==============================="
for js in *.js; do
    [ -f "$js" ] && compress_file "$js"
done

echo "Compressing other static assets..."
echo "==================================="
for asset in *.html *.css *.json; do
    [ -f "$asset" ] && compress_file "$asset"
done

echo -e "${GREEN}╔═══════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║        Compression complete!              ║${NC}"
echo -e "${GREEN}╚═══════════════════════════════════════════╝${NC}"
echo ""
echo "Compressed files are ready in: $BUILD_DIR"
echo ""
echo "To serve with nginx, add to your config:"
echo "  gzip_static on;"
echo "  brotli_static on;  # if using ngx_brotli module"
