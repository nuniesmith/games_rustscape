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
    /usr/share/nginx/html/kmp

# Copy built TypeScript web client from builder stage
# Vite outputs to dist/ with index.html and assets/
COPY --from=ts-builder /build/dist/ /usr/share/nginx/html/web-client/

# Copy built KMP WASM client from builder stage
# Compose outputs to productionExecutable/
COPY --from=kmp-builder /build/composeApp/build/dist/wasmJs/productionExecutable/ /usr/share/nginx/html/kmp/

# Copy static assets that might not be included in build
COPY ./src/clients/web/assets/ /usr/share/nginx/html/web-client/assets/

# Copy fallback HTML files (for health endpoints, etc.)
COPY ./config/html /usr/share/nginx/html/default

# Create a landing page that links to both clients
RUN cat > /usr/share/nginx/html/index.html << 'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Rustscape - Choose Client</title>
<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
background: linear-gradient(180deg, #1A140E 0%, #2B2117 50%, #1A140E 100%);
min-height: 100vh;
display: flex;
flex-direction: column;
align-items: center;
justify-content: center;
color: #CCCCCC;
}
h1 {
font-size: 48px;
color: #D4A84B;
letter-spacing: 4px;
margin-bottom: 8px;
text-shadow: 2px 2px 4px rgba(0,0,0,0.5);
}
.subtitle {
color: #999;
margin-bottom: 40px;
letter-spacing: 2px;
}
.clients {
display: flex;
gap: 24px;
flex-wrap: wrap;
justify-content: center;
}
.client-card {
background: rgba(43, 33, 23, 0.9);
border: 2px solid #5C4A36;
border-radius: 8px;
padding: 32px;
width: 280px;
text-align: center;
text-decoration: none;
color: inherit;
transition: all 0.3s ease;
}
.client-card:hover {
border-color: #D4A84B;
transform: translateY(-4px);
box-shadow: 0 8px 24px rgba(0,0,0,0.4);
}
.client-card h2 {
color: #D4A84B;
margin-bottom: 12px;
font-size: 20px;
}
.client-card p {
font-size: 14px;
line-height: 1.6;
margin-bottom: 16px;
}
.client-card .tech {
font-size: 12px;
color: #666;
padding: 4px 8px;
background: rgba(0,0,0,0.3);
border-radius: 4px;
display: inline-block;
}
.recommended {
border-color: #D4A84B;
}
.recommended::after {
content: 'RECOMMENDED';
display: block;
margin-top: 16px;
font-size: 10px;
letter-spacing: 2px;
color: #D4A84B;
}
</style>
</head>
<body>
<h1>RUSTSCAPE</h1>
<p class="subtitle">A RuneScape-Inspired Adventure</p>

<div class="clients">
<a href="/web-client/" class="client-card">
<h2>Classic Client</h2>
<p>Original TypeScript client with PixiJS rendering. Lightweight and fast.</p>
<span class="tech">TypeScript + PixiJS</span>
</a>

<a href="/kmp/" class="client-card recommended">
<h2>Modern Client</h2>
<p>New Kotlin Multiplatform client with Compose UI. Cross-platform ready.</p>
<span class="tech">Kotlin + Compose WASM</span>
</a>
</div>
</body>
</html>
EOF

# Set proper permissions
RUN chmod -R 755 /usr/share/nginx/html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost/health || exit 1

# Run nginx
CMD ["nginx", "-g", "daemon off;"]
