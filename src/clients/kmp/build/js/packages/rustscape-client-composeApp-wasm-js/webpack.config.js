let config = {
  mode: 'production',
  resolve: {
    modules: [
      "node_modules"
    ]
  },
  plugins: [],
  module: {
    rules: []
  }
};

// entry
config.entry = {
    main: [require('path').resolve(__dirname, "kotlin/rustscape-client-composeApp-wasm-js.mjs")]
};
config.output = {
    filename: (chunkData) => {
        return chunkData.chunk.name === 'main'
            ? "rustscape-client.js"
            : "rustscape-client-[name].js";
    },
    library: "composeApp",
    libraryTarget: "umd",
    globalObject: "globalThis"
};
config.output.path = require('path').resolve(__dirname, "../../../../composeApp/build/kotlin-webpack/wasmJs/productionExecutable")
// source maps
config.module.rules.push({
        test: /\.m?js$/,
        use: ["source-map-loader"],
        enforce: "pre"
});
config.devtool = 'source-map';
config.ignoreWarnings = [
    /Failed to parse source map/,
    /Accessing import\.meta directly is unsupported \(only property access or destructuring is supported\)/
]

// noinspection JSUnnecessarySemicolon
;(function(config) {
    const tcErrorPlugin = require('kotlin-web-helpers/dist/tc-log-error-webpack');
    config.plugins.push(new tcErrorPlugin())
    config.stats = config.stats || {}
    Object.assign(config.stats, config.stats, {
        warnings: false,
        errors: false
    })
})(config);

// optimize.js
// Webpack optimization configuration for Rustscape KMP WASM client
// This file is automatically merged with the generated webpack config

const TerserPlugin = require('terser-webpack-plugin');

config.optimization = config.optimization || {};

// Enable production optimizations
if (config.mode === 'production') {
    // Minimize JavaScript output
    config.optimization.minimize = true;

    config.optimization.minimizer = [
        new TerserPlugin({
            terserOptions: {
                compress: {
                    // Remove console.log in production (keep errors and warnings)
                    pure_funcs: ['console.log'],
                    drop_debugger: true,
                    dead_code: true,
                    unused: true,
                    passes: 2
                },
                mangle: {
                    // Mangle property names for smaller output
                    properties: false // Keep false for WASM interop safety
                },
                output: {
                    comments: false
                }
            },
            extractComments: false
        })
    ];

    // Split chunks for better caching
    config.optimization.splitChunks = {
        chunks: 'all',
        minSize: 20000,
        maxSize: 250000,
        cacheGroups: {
            // Separate vendor code (kotlinx, ktor, etc.)
            vendors: {
                test: /[\\/]node_modules[\\/]/,
                name: 'vendors',
                chunks: 'all',
                priority: 10
            },
            // Separate WASM runtime
            wasm: {
                test: /\.wasm$/,
                name: 'wasm-runtime',
                chunks: 'all',
                priority: 20
            }
        }
    };

    // Use deterministic module IDs for better caching
    config.optimization.moduleIds = 'deterministic';
    config.optimization.chunkIds = 'deterministic';
}

// Enable WASM async loading for better initial load
config.experiments = config.experiments || {};
config.experiments.asyncWebAssembly = true;

// Performance hints configuration
config.performance = {
    hints: 'warning',
    maxAssetSize: 512000,      // 500 KiB
    maxEntrypointSize: 512000  // 500 KiB
};

// Output configuration for better compression
config.output = config.output || {};
config.output.hashFunction = 'xxhash64';



config.experiments = {
    asyncWebAssembly: true,
    topLevelAwait: true,
}
module.exports = config
