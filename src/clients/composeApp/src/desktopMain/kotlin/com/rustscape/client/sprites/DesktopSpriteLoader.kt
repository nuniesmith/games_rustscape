package com.rustscape.client.sprites

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.rustscape.client.ui.components.PRELOAD_GAME_SPRITES
import com.rustscape.client.ui.components.SpriteCache
import com.rustscape.client.ui.components.SpriteIds
import com.rustscape.client.ui.components.SpriteLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Desktop-based sprite loader that loads PNG sprites from the local filesystem
 * Sprites are expected to be in assets/rendering/sprites/{id}.png relative to working dir
 * or in a configured sprites directory
 */
class DesktopSpriteLoader(
    override val basePath: String = findSpritesPath()
) : SpriteLoader {

    private var totalLoaded = 0
    private var totalFailed = 0

    /**
     * Load a single sprite by ID from the filesystem
     */
    override suspend fun loadSprite(spriteId: Int): ImageBitmap? {
        val filePath = getSpriteUrl(spriteId)

        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    println("[DesktopSpriteLoader] Sprite file not found: $filePath")
                    totalFailed++
                    return@withContext null
                }

                val bytes = Files.readAllBytes(file.toPath())
                val skiaImage = Image.makeFromEncoded(bytes)
                val bitmap = skiaImage.toComposeImageBitmap()
                totalLoaded++
                bitmap
            } catch (e: Exception) {
                println("[DesktopSpriteLoader] Failed to load sprite $spriteId from $filePath: ${e.message}")
                totalFailed++
                null
            }
        }
    }

    /**
     * Load a sprite frame (for multi-frame sprites like animations)
     */
    suspend fun loadSpriteFrame(spriteId: Int, frame: Int): ImageBitmap? {
        val filePath = getFrameSpriteUrl(spriteId, frame)

        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext null
                }

                val bytes = Files.readAllBytes(file.toPath())
                val skiaImage = Image.makeFromEncoded(bytes)
                skiaImage.toComposeImageBitmap()
            } catch (e: Exception) {
                println("[DesktopSpriteLoader] Failed to load sprite frame $spriteId/$frame from $filePath")
                null
            }
        }
    }

    /**
     * Load a sprite from an external URL via HTTP
     * Used for loading item sprites from external sources like OSRS Wiki
     */
    override suspend fun loadFromUrl(url: String): ImageBitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "Rustscape-Client/1.0")

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val bytes = connection.inputStream.readBytes()
                    val skiaImage = Image.makeFromEncoded(bytes)
                    skiaImage.toComposeImageBitmap()
                } else {
                    println("[DesktopSpriteLoader] HTTP ${connection.responseCode} when loading from URL: $url")
                    null
                }
            } catch (e: Exception) {
                println("[DesktopSpriteLoader] Failed to load sprite from URL $url: ${e.message}")
                null
            }
        }
    }

    /**
     * Load multiple sprites in parallel
     */
    override suspend fun loadSprites(spriteIds: List<Int>): Map<Int, ImageBitmap?> {
        val results = mutableMapOf<Int, ImageBitmap?>()

        coroutineScope {
            spriteIds.forEach { spriteId ->
                launch {
                    results[spriteId] = loadSprite(spriteId)
                }
            }
        }

        return results
    }

    /**
     * Preload commonly used sprites (skill icons, UI elements, etc.)
     */
    override suspend fun preloadCommon() {
        println("[DesktopSpriteLoader] Preloading ${PRELOAD_GAME_SPRITES.size} common sprites from $basePath...")

        val startTime = System.currentTimeMillis()

        coroutineScope {
            PRELOAD_GAME_SPRITES.forEach { spriteId ->
                launch {
                    if (!SpriteCache.has(spriteId)) {
                        SpriteCache.markLoading(spriteId)
                        val bitmap = loadSprite(spriteId)
                        if (bitmap != null) {
                            SpriteCache.put(spriteId, bitmap)
                        } else {
                            SpriteCache.markFailed(spriteId)
                        }
                    }
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        val stats = SpriteCache.getStats()
        println("[DesktopSpriteLoader] Preload complete in ${elapsed}ms: ${stats.loadedCount} loaded, ${stats.failedCount} failed")
    }

    /**
     * Preload skill icons specifically
     */
    suspend fun preloadSkillIcons() {
        println("[DesktopSpriteLoader] Preloading skill icons...")
        loadSprites(SpriteIds.Skills.ALL)
    }

    /**
     * Get loader statistics
     */
    fun getStats(): LoaderStats {
        return LoaderStats(
            totalLoaded = totalLoaded,
            totalFailed = totalFailed,
            cacheStats = SpriteCache.getStats()
        )
    }

    data class LoaderStats(
        val totalLoaded: Int,
        val totalFailed: Int,
        val cacheStats: SpriteCache.CacheStats
    )

    companion object {
        /**
         * Find the sprites path by checking common locations
         */
        fun findSpritesPath(): String {
            val possiblePaths = listOf(
                // Relative to working directory
                "assets/rendering/sprites",
                "../assets/rendering/sprites",
                "../../assets/rendering/sprites",
                // Absolute paths for development
                System.getProperty("rustscape.sprites.path", ""),
                // User home based
                "${System.getProperty("user.home")}/.rustscape/sprites"
            ).filter { it.isNotEmpty() }

            for (path in possiblePaths) {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    // Check if it has sprite files
                    val hasSprites = dir.listFiles()?.any { it.name.endsWith(".png") } == true
                    if (hasSprites) {
                        println("[DesktopSpriteLoader] Found sprites directory: ${dir.absolutePath}")
                        return dir.absolutePath
                    }
                }
            }

            // Default fallback - will likely fail but provides clear error messages
            println("[DesktopSpriteLoader] Warning: Could not find sprites directory, using default path")
            return "assets/rendering/sprites"
        }

        /**
         * Check if sprites are available
         */
        fun isSpritesAvailable(): Boolean {
            val path = findSpritesPath()
            val dir = File(path)
            return dir.exists() && dir.isDirectory &&
                    (dir.listFiles()?.any { it.name.endsWith(".png") } == true)
        }

        /**
         * Get count of available sprites
         */
        fun getAvailableSpriteCount(): Int {
            val path = findSpritesPath()
            val dir = File(path)
            return if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.count { it.name.endsWith(".png") } ?: 0
            } else 0
        }
    }
}
