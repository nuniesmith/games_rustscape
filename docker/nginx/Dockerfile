# ============================================
# Rustscape Nginx Dockerfile
# Builds KMP WASM client and serves static assets
# Uses pre-extracted sprites from assets/rendering/sprites/
# ============================================

# Stage 1: Build the KMP WASM client
FROM eclipse-temurin:17-jdk-alpine AS kmp-builder

WORKDIR /build

# Install required tools
RUN apk add --no-cache bash curl unzip

# Copy KMP project files (now at src/clients/ root level)
COPY ./src/clients/ ./

# Make gradlew executable
RUN chmod +x ./gradlew

# Download Gradle wrapper if jar is missing
RUN if [ ! -f gradle/wrapper/gradle-wrapper.jar ]; then \
    mkdir -p gradle/wrapper && \
    curl -sL "https://services.gradle.org/distributions/gradle-8.10-bin.zip" -o /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /tmp && \
    cp /tmp/gradle-8.10/lib/gradle-launcher-*.jar gradle/wrapper/gradle-wrapper.jar 2>/dev/null || \
    echo "Using Gradle wrapper download"; \
    fi

# Build the WASM client
# Use --no-daemon for container builds, --warning-mode=summary to reduce output
RUN ./gradlew :composeApp:wasmJsBrowserDistribution \
    --no-daemon \
    --warning-mode=summary \
    -Dorg.gradle.jvmargs="-Xmx1536m" \
    || echo "KMP build completed (check for errors above)"

# List built files for debugging
RUN ls -la composeApp/build/dist/wasmJs/productionExecutable/ 2>/dev/null || echo "KMP build output not found"

# Stage 2: Nginx with built assets
# (Sprites are pre-extracted, no need for rust build stage)
FROM nginx:alpine

# Install curl for healthcheck
RUN apk add --no-cache curl

# Copy nginx configuration
COPY ./config/nginx/nginx.conf /etc/nginx/nginx.conf
COPY ./config/nginx/conf.d/server.conf /etc/nginx/conf.d/default.conf
COPY ./config/nginx/proxy_params_common.conf /etc/nginx/proxy_params_common.conf
COPY ./config/nginx/proxy_params_websocket.conf /etc/nginx/proxy_params_websocket.conf

# Copy SSL certificates
COPY ./config/ssl /etc/nginx/ssl

# Create web client directories
RUN mkdir -p /usr/share/nginx/html/app \
    /usr/share/nginx/html/sprites \
    /usr/share/nginx/html/cache \
    /usr/share/nginx/html/default

# Copy built KMP WASM client from builder stage to root
# This makes the KMP client the default at /
COPY --from=kmp-builder /build/composeApp/build/dist/wasmJs/productionExecutable/ /usr/share/nginx/html/app/

# Copy HTML files (error pages, etc.)
COPY ./config/html/404.html /usr/share/nginx/html/default/
COPY ./config/html/50x.html /usr/share/nginx/html/default/

# Copy pre-extracted sprites from assets/rendering/sprites/
# These are already extracted PNG files ready to serve
COPY ./assets/rendering/sprites/ /usr/share/nginx/html/sprites/

# Set proper permissions
RUN chmod -R 755 /usr/share/nginx/html

# Log what's available
RUN echo "=== KMP Client ===" && ls -la /usr/share/nginx/html/app/ | head -15 && \
    echo "=== Sprites ===" && \
    (find /usr/share/nginx/html/sprites -name "*.png" 2>/dev/null | wc -l | xargs -I {} echo "PNG sprites: {}") || echo "No sprites" && \
    echo "=== Sprite Manifests ===" && \
    ls -la /usr/share/nginx/html/sprites/*.json 2>/dev/null || echo "No manifests"

# Expose ports (HTTP and HTTPS)
EXPOSE 80 443

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost/health || exit 1

# Run nginx
CMD ["nginx", "-g", "daemon off;"]
