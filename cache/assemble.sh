#!/bin/bash
# Assemble split cache files into main_file_cache.dat2
#
# The cache data file is split into 20MB chunks for GitHub storage.
# This script reassembles them into the original file.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "Rustscape Cache Assembler"
echo "========================="

# Check if already assembled
if [ -f "main_file_cache.dat2" ]; then
    echo -e "${YELLOW}[INFO]${NC} main_file_cache.dat2 already exists"

    # Verify size
    size=$(stat -f%z "main_file_cache.dat2" 2>/dev/null || stat -c%s "main_file_cache.dat2" 2>/dev/null)
    if [ "$size" -gt 80000000 ]; then
        echo -e "${GREEN}[OK]${NC} File size looks correct ($size bytes)"
        exit 0
    else
        echo -e "${YELLOW}[WARN]${NC} File seems too small, reassembling..."
        rm -f main_file_cache.dat2
    fi
fi

# Check for parts
parts=$(ls main_file_cache.dat2.part_* 2>/dev/null | wc -l)
if [ "$parts" -eq 0 ]; then
    echo -e "${RED}[ERROR]${NC} No cache parts found (main_file_cache.dat2.part_*)"
    echo "Make sure you have the split cache files in this directory."
    exit 1
fi

echo "Found $parts cache parts"

# Assemble
echo "Assembling cache file..."
cat main_file_cache.dat2.part_* > main_file_cache.dat2

if [ $? -eq 0 ]; then
    size=$(stat -f%z "main_file_cache.dat2" 2>/dev/null || stat -c%s "main_file_cache.dat2" 2>/dev/null)
    echo -e "${GREEN}[SUCCESS]${NC} Assembled main_file_cache.dat2 ($size bytes)"

    # Verify we have index files
    idx_count=$(ls main_file_cache.idx* 2>/dev/null | wc -l)
    echo -e "${GREEN}[OK]${NC} Found $idx_count index files"

    echo ""
    echo "Cache is ready to use!"
else
    echo -e "${RED}[ERROR]${NC} Failed to assemble cache file"
    exit 1
fi
