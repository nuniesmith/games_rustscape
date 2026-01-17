plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // JVM target for Desktop (Linux, Windows, macOS)
    jvm("desktop")

    // Web target with WebAssembly
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "rustscape-shared.js"
            }
        }
    }

    // iOS targets (for future mobile support)
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // Source set configuration
    sourceSets {
        commonMain.dependencies {
            // Coroutines for async operations
            implementation(libs.kotlinx.coroutines.core)

            // Serialization for data classes
            implementation(libs.kotlinx.serialization.json)

            // DateTime handling
            implementation(libs.kotlinx.datetime)

            // Note: Ktor and Okio removed - using native WebSocket API and custom ByteBuffer
            // This significantly reduces WASM bundle size
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        val desktopMain by getting {
            dependencies {
                // Swing coroutines for desktop UI
                implementation(libs.kotlinx.coroutines.swing)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val wasmJsMain by getting {
            dependencies {
                // JS engine handled automatically for WASM
            }
        }

        // iOS source sets
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting

        val iosMain by creating {
            dependsOn(commonMain.get())
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)

            dependencies {
                // iOS-specific dependencies can be added here when needed
            }
        }

        val iosTest by creating {
            dependsOn(commonTest.get())
        }
    }
}
