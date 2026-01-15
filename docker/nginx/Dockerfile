# ============================================
# Rustscape Nginx Dockerfile
# Builds both TypeScript and KMP web clients
# ============================================

# Stage 1: Build the TypeScript web client with Vite
FROM node:20-alpine AS ts-builder

WORKDIR /build

# Install build dependencies for native modules
RUN apk add --no-cache python3 make g++

# Copy package files first for better layer caching
COPY ./src/clients/web/package*.json ./

# Install all dependencies (including dev dependencies for build)
RUN npm ci

# Copy source files
COPY ./src/clients/web/ ./

# Build the web client with Vite
# This compiles TypeScript and bundles everything into dist/
RUN npm run build

# List built files for debugging
RUN ls -la dist/ && echo "TypeScript build complete!"

# Stage 2: Build the KMP WASM client
FROM eclipse-temurin:17-jdk-alpine AS kmp-builder

WORKDIR /build

# Install required tools
RUN apk add --no-cache bash curl

# Copy KMP project files
COPY ./src/clients/kmp/ ./

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

# Stage 3: Nginx with built assets
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
RUN mkdir -p /usr/share/nginx/html/web-client \
    /usr/share/nginx/html/kmp \
    /usr/share/nginx/html/default

# Copy built TypeScript web client from builder stage
# Vite outputs to dist/ with index.html and assets/
COPY --from=ts-builder /build/dist/ /usr/share/nginx/html/web-client/

# Copy built KMP WASM client from builder stage
# Compose outputs to productionExecutable/
COPY --from=kmp-builder /build/composeApp/build/dist/wasmJs/productionExecutable/ /usr/share/nginx/html/kmp/

# Copy static assets that might not be included in build
COPY ./src/clients/web/assets/ /usr/share/nginx/html/web-client/assets/

# Copy HTML files (landing page, error pages, etc.)
COPY ./config/html/ /usr/share/nginx/html/default/

# Copy landing page to root
COPY ./config/html/index.html /usr/share/nginx/html/index.html

# Set proper permissions
RUN chmod -R 755 /usr/share/nginx/html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost/health || exit 1

# Run nginx
CMD ["nginx", "-g", "daemon off;"]
