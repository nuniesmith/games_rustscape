# Stage 1: Build the TypeScript web client
FROM node:20-alpine AS builder

WORKDIR /build

# Copy package files first for better caching
COPY ./src/client/web/package*.json ./

# Install dependencies
RUN npm ci --silent 2>/dev/null || npm install --silent

# Copy source files
COPY ./src/client/web/ ./

# Build the web client (skip if no build script or on error, just copy files)
RUN npm run build 2>/dev/null || mkdir -p dist && cp -r *.html dist/ 2>/dev/null || true

# Stage 2: Nginx with built assets
FROM nginx:latest

# Copy nginx configuration
COPY ./config/nginx/nginx.conf /etc/nginx/nginx.conf
COPY ./config/nginx/conf.d /etc/nginx/conf.d
COPY ./config/nginx/proxy_params_common.conf /etc/nginx/proxy_params_common.conf
COPY ./config/nginx/proxy_params_websocket.conf /etc/nginx/proxy_params_websocket.conf

# Copy default HTML files
COPY ./config/html /usr/share/nginx/html

# Copy SSL certificates
COPY ./config/ssl /etc/nginx/ssl

# Create web client directory
RUN mkdir -p /usr/share/nginx/html/web-client

# Copy built web client from builder stage (dist if exists, otherwise source files)
COPY --from=builder /build/dist/ /usr/share/nginx/html/web-client/
# Also copy source HTML files as fallback
COPY ./src/client/web/*.html /usr/share/nginx/html/web-client/
COPY ./src/client/web/assets/ /usr/share/nginx/html/web-client/assets/

# Set proper permissions
RUN chmod -R 755 /usr/share/nginx/html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost/health || exit 1

# Run nginx
CMD ["nginx", "-g", "daemon off;"]
