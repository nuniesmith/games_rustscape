###############################
# Stage 1: Builder
###############################
FROM rust:latest AS builder

WORKDIR /build

# Install build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    pkg-config \
    libssl-dev \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Copy Cargo files first for dependency caching
COPY src/server/Cargo.toml src/server/Cargo.lock* ./

# Create dummy lib.rs and main.rs to build dependencies
RUN mkdir -p src/bin && \
    echo "fn main() {}" > src/main.rs && \
    echo "" > src/lib.rs && \
    echo "fn main() {}" > src/bin/extract_sprites.rs && \
    cargo build --release && \
    rm -rf src

# Copy actual source code
COPY src/server/src ./src

# Build the actual application (touch lib.rs and main.rs to invalidate cached empty files)
RUN touch src/lib.rs src/main.rs && cargo build --release

###############################
# Stage 2: Runtime
###############################
FROM debian:bookworm-slim

# Create non-root user
ARG USER_NAME=rustscape
ARG USER_ID=1000
ARG GROUP_ID=1000

# Install runtime dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    libssl3 \
    tini \
    netcat-openbsd \
    curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g ${GROUP_ID} ${USER_NAME} \
    && useradd -u ${USER_ID} -g ${GROUP_ID} -m ${USER_NAME}

# Set up working directory
WORKDIR /app

# Copy binary from builder stage
COPY --from=builder /build/target/release/rustscape-server /app/rustscape-server

# Copy configuration
COPY config/server.toml /app/config/server.toml

# Create data directories
RUN mkdir -p /app/data/cache /app/data/extracted/sprites /app/logs \
    && chown -R ${USER_NAME}:${USER_NAME} /app

# Switch to non-root user
USER ${USER_NAME}

# Environment variables
ENV RUST_LOG=info \
    RUSTSCAPE_CONFIG_PATH=/app/config/server.toml \
    RUSTSCAPE_CACHE_PATH=/app/data/cache \
    RUSTSCAPE_DATA_PATH=/app/data

# Expose ports
# 43594 - Base game port (TCP)
# 43595 - World 1 game port (TCP)
# 43596 - WebSocket port
# 5555  - Management API
EXPOSE 43594 43595 43596 5555

# Volumes for persistent data
VOLUME ["/app/data", "/app/logs"]

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD nc -z localhost 43596 || exit 1

# Use tini as init system
ENTRYPOINT ["/usr/bin/tini", "--"]

# Run the server
CMD ["/app/rustscape-server"]
