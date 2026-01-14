import { defineConfig } from 'vite';
import { resolve } from 'path';

export default defineConfig({
    root: '.',
    base: '/',
    publicDir: 'assets',

    resolve: {
        alias: {
            '@': resolve(__dirname, 'src'),
            '@protocol': resolve(__dirname, 'src/protocol'),
            '@cache': resolve(__dirname, 'src/cache'),
            '@game': resolve(__dirname, 'src/game'),
            '@render': resolve(__dirname, 'src/render'),
            '@ui': resolve(__dirname, 'src/ui'),
        },
    },

    server: {
        port: 5173,
        host: true,
        proxy: {
            // Proxy WebSocket connections to the game server
            '/ws': {
                target: 'ws://localhost:43596',
                ws: true,
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/ws/, ''),
            },
            // Proxy API calls to the game server management port
            '/api': {
                target: 'http://localhost:5555',
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/api/, ''),
            },
        },
    },

    build: {
        outDir: 'dist',
        sourcemap: true,
        minify: 'terser',
        rollupOptions: {
            input: {
                main: resolve(__dirname, 'index.html'),
                test: resolve(__dirname, 'test.html'),
                cacheBrowser: resolve(__dirname, 'cache-browser.html'),
            },
            output: {
                manualChunks: {
                    vendor: ['pako'],
                },
            },
        },
    },

    optimizeDeps: {
        include: ['pako'],
    },
});
