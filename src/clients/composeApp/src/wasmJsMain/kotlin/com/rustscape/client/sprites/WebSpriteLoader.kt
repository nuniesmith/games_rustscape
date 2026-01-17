package com.rustscape.client.sprites

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.rustscape.client.ui.components.PRELOAD_GAME_SPRITES
import com.rustscape.client.ui.components.SpriteCache
import com.rustscape.client.ui.components.SpriteIds
import com.rustscape.client.ui.components.SpriteLoader
import kotlinx.coroutines.await
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import kotlin.js.Promise

/**
 * External JS functions for image loading
 */
@JsFun("(url) => fetch(url).then(r => r.ok ? r.arrayBuffer() : Promise.reject('HTTP ' + r.status))")
private external fun fetchArrayBuffer(url: String): Promise<ArrayBuffer>

@JsFun("(msg) => console.log(msg)")
private external fun consoleLog(msg: String)

@JsFun("(msg) => console.error(msg)")
private external fun consoleError(msg: String)

@JsFun("() => Date.now()")
private external fun currentTimeMillis(): Double

/**
 * Web-based sprite loader that fetches PNG sprites from the server
 * Sprites are expected to be served from /sprites/{id}.png
 */
class WebSpriteLoader(
    override val basePath: String = "/sprites"
) : SpriteLoader {

    private var totalLoaded = 0
    private var totalFailed = 0

    /**
     * Load a single sprite by ID
     */
    override suspend fun loadSprite(spriteId: Int): ImageBitmap? {
        val url = getSpriteUrl(spriteId)

        return try {
            val arrayBuffer = fetchArrayBuffer(url).await<ArrayBuffer>()
            val bytes = arrayBufferToByteArray(arrayBuffer)
            val skiaImage = Image.makeFromEncoded(bytes)
            val bitmap = skiaImage.toComposeImageBitmap()
            totalLoaded++
            bitmap
        } catch (e: Exception) {
            consoleError("[WebSpriteLoader] Failed to load sprite $spriteId from $url: ${e.message}")
            totalFailed++
            null
        }
    }

    /**
     * Load a sprite frame (for multi-frame sprites like animations)
     */
    suspend fun loadSpriteFrame(spriteId: Int, frame: Int): ImageBitmap? {
        val url = getFrameSpriteUrl(spriteId, frame)

        return try {
            val arrayBuffer = fetchArrayBuffer(url).await<ArrayBuffer>()
            val bytes = arrayBufferToByteArray(arrayBuffer)
            val skiaImage = Image.makeFromEncoded(bytes)
            skiaImage.toComposeImageBitmap()
        } catch (e: Exception) {
            consoleError("[WebSpriteLoader] Failed to load sprite frame $spriteId/$frame from $url")
            null
        }
    }

    /**
     * Load a sprite from an external URL
     * Used for loading item sprites from external sources like OSRS Wiki
     */
    override suspend fun loadFromUrl(url: String): ImageBitmap? {
        return try {
            val arrayBuffer = fetchArrayBuffer(url).await<ArrayBuffer>()
            val bytes = arrayBufferToByteArray(arrayBuffer)
            val skiaImage = Image.makeFromEncoded(bytes)
            skiaImage.toComposeImageBitmap()
        } catch (e: Exception) {
            consoleError("[WebSpriteLoader] Failed to load sprite from URL $url: ${e.message}")
            null
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
        consoleLog("[WebSpriteLoader] Preloading ${PRELOAD_GAME_SPRITES.size} common sprites...")

        val startTime = currentTimeMillis().toLong()

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

        val elapsed = currentTimeMillis().toLong() - startTime
        val stats = SpriteCache.getStats()
        consoleLog("[WebSpriteLoader] Preload complete in ${elapsed}ms: ${stats.loadedCount} loaded, ${stats.failedCount} failed")
    }

    /**
     * Preload skill icons specifically
     */
    suspend fun preloadSkillIcons() {
        consoleLog("[WebSpriteLoader] Preloading skill icons...")
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
         * Convert JS ArrayBuffer to Kotlin ByteArray
         */
        private fun arrayBufferToByteArray(buffer: ArrayBuffer): ByteArray {
            val int8Array = Int8Array(buffer)
            val length = int8Array.length
            val bytes = ByteArray(length)
            for (i in 0 until length) {
                bytes[i] = int8Array[i]
            }
            return bytes
        }

    }
}
