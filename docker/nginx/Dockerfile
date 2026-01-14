# Stage 1: Build the TypeScript web client with Vite
FROM node:20-alpine AS builder

WORKDIR /build

# Install build dependencies for native modules
RUN apk add --no-cache python3 make g++

# Copy package files first for better layer caching
COPY ./src/client/web/package*.json ./

# Install all dependencies (including dev dependencies for build)
RUN npm ci

# Copy source files
COPY ./src/client/web/ ./

# Build the web client with Vite
# This compiles TypeScript and bundles everything into dist/
RUN npm run build

# List built files for debugging
RUN ls -la dist/ && echo "Build complete!"

# Stage 2: Nginx with built assets
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

# Create web client directory
RUN mkdir -p /usr/share/nginx/html/web-client

# Copy built web client from builder stage
# Vite outputs to dist/ with index.html and assets/
COPY --from=builder /build/dist/ /usr/share/nginx/html/web-client/

# Copy static assets that might not be included in build
COPY ./src/client/web/assets/ /usr/share/nginx/html/web-client/assets/

# Copy fallback HTML files (for health endpoints, etc.)
COPY ./config/html /usr/share/nginx/html/default

# Set proper permissions
RUN chmod -R 755 /usr/share/nginx/html

# Create a simple index redirect if needed
RUN echo '<!DOCTYPE html><html><head><meta http-equiv="refresh" content="0;url=/web-client/"></head></html>' > /usr/share/nginx/html/index.html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost/health || exit 1

# Run nginx
CMD ["nginx", "-g", "daemon off;"]
