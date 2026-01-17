import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // JVM target for Desktop (Linux, Windows, macOS)
    jvm("desktop")

    // WebAssembly target for browser
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "rustscape-client.js"
                // Enable production optimizations
                devServer?.open = false
            }
            // Enable Binaryen optimization for smaller WASM output
            applyBinaryen {
                binaryenArgs = mutableListOf(
                    "-O3",              // Maximum optimization level
                    "--enable-gc",      // Enable GC for Kotlin WASM
                    "--enable-strings", // Enable string builtins
                    "--closed-world",   // Assume closed world for better optimization
                    "--traps-never-happen" // Assume traps won't happen (faster code)
                )
            }
            binaries.executable()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Shared module
                implementation(project(":shared"))

                // Compose dependencies
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val desktopMain by getting {
            dependencies {
                // Desktop-specific Compose
                implementation(compose.desktop.currentOs)

                // Swing coroutines for desktop
                implementation(libs.kotlinx.coroutines.swing)

                // Ktor CIO engine for desktop networking
                implementation(libs.ktor.client.cio)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                // WASM/JS specific dependencies are auto-included
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.rustscape.client.MainKt"

        nativeDistributions {
            // Target formats for distribution
            targetFormats(
                // Linux
                TargetFormat.Deb,       // Debian/Ubuntu .deb package
                TargetFormat.Rpm,       // Fedora/RHEL .rpm package
                TargetFormat.AppImage,  // Universal Linux AppImage

                // Windows
                TargetFormat.Msi,       // Windows installer
                TargetFormat.Exe,       // Windows executable installer

                // macOS
                TargetFormat.Dmg,       // macOS disk image
                TargetFormat.Pkg        // macOS package installer
            )

            // Package metadata
            packageName = "Rustscape"
            packageVersion = "1.0.0"
            description = "Rustscape Game Client - A RuneScape-inspired MMORPG"
            copyright = "© 2024 Rustscape"
            vendor = "Rustscape"

            // Linux-specific configuration
            linux {
                iconFile.set(project.file("src/desktopMain/resources/icons/icon.png"))
                debMaintainer = "rustscape@example.com"
                menuGroup = "Games"
                appCategory = "Game"
                // For creating tar.gz, use: ./gradlew packageAppImage then tar the result
            }

            // Windows-specific configuration
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icons/icon.ico"))
                menuGroup = "Rustscape"
                // Generate a UUID for upgrade code (should be consistent across versions)
                upgradeUuid = "8b3c4d5e-6f7a-8b9c-0d1e-2f3a4b5c6d7e"
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }

            // macOS-specific configuration
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icons/icon.icns"))
                bundleID = "com.rustscape.client"
                appCategory = "public.app-category.games"
                signing {
                    sign.set(false) // Set to true and configure for production
                }
            }

            // JVM arguments for the application
            jvmArgs(
                "-Xmx512m",
                "-Xms128m"
            )

            // Module configuration
            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.net.http",
                "jdk.crypto.ec"
            )
        }

        // Development/debug configuration
        buildTypes.release {
            proguard {
                isEnabled.set(false) // Enable for production with proper rules
            }
        }
    }
}

// Custom task to create Linux tar.gz distribution
tasks.register<Tar>("packageLinuxTarGz") {
    dependsOn("packageAppImage")

    archiveBaseName.set("rustscape-client")
    archiveVersion.set("1.0.0")
    archiveExtension.set("tar.gz")
    compression = Compression.GZIP

    from(layout.buildDirectory.dir("compose/binaries/main/app/Rustscape"))
    into("rustscape-client-1.0.0")

    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}

// Task to build all distributions
tasks.register("packageAll") {
    dependsOn(
        "packageDeb",
        "packageRpm",
        "packageAppImage",
        "packageLinuxTarGz",
        "packageMsi",
        "packageDmg"
    )

    doLast {
        println("All distributions have been built!")
        println("Output directory: ${layout.buildDirectory.dir("compose/binaries/main").get()}")
    }
}

// Task to build optimized WASM production bundle
tasks.register("buildOptimizedWasm") {
    group = "build"
    description = "Build optimized WASM bundle with compression for production"

    dependsOn("wasmJsBrowserProductionWebpack")

    doLast {
        val buildDir = layout.buildDirectory.dir("dist/wasmJs/productionExecutable").get().asFile

        println("")
        println("╔═══════════════════════════════════════════════════════════╗")
        println("║          Optimized WASM Build Complete!                   ║")
        println("╚═══════════════════════════════════════════════════════════╝")
        println("")

        // Report file sizes
        if (buildDir.exists()) {
            println("Bundle sizes:")
            buildDir.listFiles()?.filter { it.extension in listOf("js", "wasm") }?.forEach { file ->
                val sizeKb = file.length() / 1024
                val sizeMb = sizeKb / 1024.0
                val sizeStr = if (sizeMb >= 1) String.format("%.2f MiB", sizeMb) else "$sizeKb KiB"
                println("  ${file.name}: $sizeStr")
            }
            println("")
            println("Output directory: $buildDir")
            println("")
            println("To further reduce transfer size, run:")
            println("  ./composeApp/compress-assets.sh")
            println("")
            println("This will create .gz and .br pre-compressed files for nginx.")
        }
    }
}

// Task to analyze bundle composition
tasks.register("analyzeBundleSize") {
    group = "help"
    description = "Analyze WASM bundle size and provide optimization tips"

    doLast {
        val buildDir = layout.buildDirectory.dir("dist/wasmJs/productionExecutable").get().asFile

        println("")
        println("╔═══════════════════════════════════════════════════════════╗")
        println("║              Bundle Size Analysis                         ║")
        println("╚═══════════════════════════════════════════════════════════╝")
        println("")

        if (!buildDir.exists()) {
            println("Build directory not found. Run './gradlew wasmJsBrowserProductionWebpack' first.")
            return@doLast
        }

        var totalSize = 0L
        val files = buildDir.listFiles()?.filter { it.extension in listOf("js", "wasm", "html") } ?: emptyList()

        files.sortedByDescending { it.length() }.forEach { file ->
            val sizeKb = file.length() / 1024
            totalSize += file.length()
            println("  ${file.name.padEnd(40)} ${sizeKb.toString().padStart(8)} KiB")
        }

        println("  ${"─".repeat(52)}")
        println("  ${"TOTAL".padEnd(40)} ${(totalSize / 1024).toString().padStart(8)} KiB")
        println("")
        println("Optimization tips:")
        println("  1. Binaryen optimization is ${if (project.findProperty("kotlin.wasm.binaryen.enable") == "true") "ENABLED ✓" else "DISABLED - add kotlin.wasm.binaryen.enable=true"}")
        println("  2. DCE (Dead Code Elimination) is ${if (project.findProperty("kotlin.js.ir.dce") == "true") "ENABLED ✓" else "DISABLED - add kotlin.js.ir.dce=true"}")
        println("  3. Pre-compress with gzip/brotli for 60-70% transfer size reduction")
        println("  4. Enable HTTP/2 on your server for multiplexed loading")
        println("")
    }
}
