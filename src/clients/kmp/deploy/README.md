# Rustscape WASM Client Deployment Guide

This guide covers deploying the Kotlin Multiplatform WASM client for production.

## Quick Start

```bash
# 1. Build production assets
./gradlew wasmJsBrowserProductionWebpack

# 2. Compress assets
cd deploy
chmod +x compress-assets.sh
./compress-assets.sh

# 3. Deploy to server
rsync -av ../composeApp/build/dist/wasmJs/productionExecutable/ user@server:/var/www/rustscape/

# 4. Configure nginx (see below)
```

## Build Process

### Development Build

```bash
./gradlew wasmJsBrowserDevelopmentRun
```

Opens a development server at `http://localhost:8080` with hot-reload.

### Production Build

```bash
./gradlew wasmJsBrowserProductionWebpack
```

Output is generated in:
```
composeApp/build/dist/wasmJs/productionExecutable/
├── index.html
├── composeApp.js
├── composeApp.wasm
├── composeApp.uninstantiated.mjs
└── ... (additional chunks)
```

## Asset Compression

The WASM bundle is large (~3-8 MB uncompressed). Precompression is essential for acceptable load times.

### Using the Compression Script

```bash
# Make executable
chmod +x deploy/compress-assets.sh

# Run compression
./deploy/compress-assets.sh

# Or specify custom output directory
./deploy/compress-assets.sh /path/to/build/output
```

The script generates:
- `.gz` files (gzip, ~70% reduction)
- `.br` files (brotli, ~75% reduction) - requires `brotli` CLI

### Install Brotli

```bash
# Ubuntu/Debian
sudo apt install brotli

# macOS
brew install brotli

# Windows (via Chocolatey)
choco install brotli
```

## Nginx Configuration

### Basic Setup

1. Copy the nginx config:
```bash
sudo cp deploy/nginx.conf /etc/nginx/sites-available/rustscape
sudo ln -s /etc/nginx/sites-available/rustscape /etc/nginx/sites-enabled/
```

2. Edit the config to set your domain:
```bash
sudo nano /etc/nginx/sites-available/rustscape
# Change: server_name rustscape.local localhost;
# To: server_name yourdomain.com;
```

3. Test and reload nginx:
```bash
sudo nginx -t
sudo nginx -s reload
```

### Key Configuration Points

#### Precompressed Assets

The nginx config uses `gzip_static on` to serve `.gz` files automatically:

```nginx
gzip_static on;
# brotli_static on;  # Uncomment if you have ngx_brotli module
```

#### WASM MIME Type

Critical for proper WASM loading:

```nginx
types {
    application/wasm wasm;
}
```

#### CORS and Security Headers

Required for WASM and SharedArrayBuffer:

```nginx
add_header Cross-Origin-Opener-Policy "same-origin" always;
add_header Cross-Origin-Embedder-Policy "require-corp" always;
```

#### Caching Strategy

- WASM/JS/CSS: Long cache (1 year, immutable)
- HTML: No cache (for instant updates)
- Images/Fonts: Medium cache (1 day to 1 year)

### Enable Brotli in Nginx

For best compression, install the brotli module:

```bash
# Ubuntu/Debian
sudo apt install libnginx-mod-brotli

# Add to nginx.conf (in http block)
brotli on;
brotli_static on;
brotli_types application/wasm application/javascript text/css text/html;
```

## HTTPS Setup (Production)

Use Let's Encrypt for free SSL:

```bash
# Install certbot
sudo apt install certbot python3-certbot-nginx

# Get certificate
sudo certbot --nginx -d yourdomain.com

# Auto-renewal is configured automatically
```

Uncomment the HTTPS server block in nginx.conf and configure your domain.

## Docker Deployment

### Dockerfile

```dockerfile
FROM nginx:alpine

# Copy nginx config
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built assets
COPY composeApp/build/dist/wasmJs/productionExecutable/ /var/www/rustscape/

# Expose port
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

### Build and Run

```bash
# Build production assets first
./gradlew wasmJsBrowserProductionWebpack

# Compress assets
./deploy/compress-assets.sh

# Build Docker image
docker build -t rustscape-client .

# Run container
docker run -d -p 80:80 rustscape-client
```

### Docker Compose

```yaml
version: '3.8'
services:
  rustscape-client:
    build: .
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./ssl:/etc/letsencrypt:ro
    restart: unless-stopped
```

## CDN Integration

For global distribution, use a CDN like Cloudflare, AWS CloudFront, or Fastly.

### Cloudflare Setup

1. Add your domain to Cloudflare
2. Enable "Auto Minify" for JS/CSS
3. Set cache rules:
   - WASM files: Cache Everything, Edge TTL: 1 month
   - JS/CSS: Cache Everything, Edge TTL: 1 month
   - HTML: Bypass Cache (or short TTL)

### AWS CloudFront

```json
{
  "CacheBehaviors": [
    {
      "PathPattern": "*.wasm",
      "Compress": true,
      "TTL": 31536000
    },
    {
      "PathPattern": "*.js",
      "Compress": true,
      "TTL": 31536000
    }
  ]
}
```

## Performance Optimization

### Expected Load Times

| Connection | Uncompressed | Gzip | Brotli |
|------------|--------------|------|--------|
| 4G (10 Mbps) | 8-10s | 2-3s | 1.5-2.5s |
| WiFi (50 Mbps) | 1.5-2s | 0.5-1s | 0.4-0.8s |
| Fiber (100+ Mbps) | <1s | <0.3s | <0.2s |

### Optimization Checklist

- [x] Enable Binaryen optimization in build (already configured)
- [x] Precompress with gzip and brotli
- [x] Configure nginx gzip_static / brotli_static
- [x] Set proper cache headers
- [ ] Use a CDN for global distribution
- [ ] Enable HTTP/2 or HTTP/3
- [ ] Consider lazy-loading non-critical modules

## Troubleshooting

### WASM Won't Load

**Symptom:** Browser console shows "Failed to fetch" or MIME type error

**Solutions:**
1. Check nginx has correct WASM MIME type
2. Verify CORS headers are set
3. Check file permissions on server
4. Ensure Cross-Origin headers are present

### Compression Not Working

**Symptom:** Network tab shows original file sizes

**Solutions:**
1. Verify `.gz` and `.br` files exist alongside originals
2. Check `gzip_static on;` is in nginx config
3. Ensure client sends `Accept-Encoding: gzip, br` header
4. Test with: `curl -I -H "Accept-Encoding: gzip" https://yourdomain.com/composeApp.wasm`

### Memory Issues

**Symptom:** Browser crashes or shows out-of-memory

**Solutions:**
1. Ensure client has sufficient RAM (4GB+ recommended)
2. Check browser compatibility (Chrome 119+, Firefox 120+, Safari 17+)
3. Consider implementing lazy-loading for large assets

### Slow Initial Load

**Solutions:**
1. Use a CDN
2. Enable HTTP/2 or HTTP/3
3. Consider code splitting (requires Kotlin/WASM support)
4. Implement a loading progress indicator

## Browser Compatibility

| Browser | Minimum Version | Notes |
|---------|-----------------|-------|
| Chrome | 119+ | Full support |
| Firefox | 120+ | Full support |
| Safari | 17+ | May need extra testing |
| Edge | 119+ | Chromium-based, same as Chrome |

Mobile browsers should work but performance may vary.

## Monitoring

### Health Check Endpoint

The nginx config includes a `/health` endpoint:

```bash
curl https://yourdomain.com/health
# Returns: OK
```

### Recommended Monitoring

- **Uptime monitoring:** Pingdom, UptimeRobot, or similar
- **Performance monitoring:** Google Lighthouse, WebPageTest
- **Error tracking:** Sentry (can be integrated into Kotlin/JS)
- **Analytics:** Google Analytics, Plausible, or similar

## Security Considerations

1. **HTTPS:** Always use HTTPS in production
2. **CSP:** Consider adding Content-Security-Policy headers
3. **HSTS:** Enable HTTP Strict Transport Security
4. **Updates:** Keep nginx and dependencies updated

## Support

For issues with the WASM client:
- Check the browser console for errors
- Verify network requests in Developer Tools
- Review nginx error logs: `/var/log/nginx/rustscape_error.log`
