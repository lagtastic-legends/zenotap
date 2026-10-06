package com.zenotap.keyboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/**
 * GifThumbnailLoader
 *
 * High-performance, zero-dependency thumbnail extraction and LRU caching
 * tailored for Android InputMethodService (IME) memory constraints.
 */
object GifThumbnailLoader {

    private const val TAG = "ZenoTapThumbLoader"
    private const val TARGET_SIZE_PX = 200

    private val maxCacheSize = (15 * 1024 * 1024).toInt() // 15MB max

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxCacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadThumbnail(file: File, imageView: ImageView) {
        val cacheKey = "${file.absolutePath}_${file.lastModified()}"

        val cached = memoryCache.get(cacheKey)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }

        imageView.setImageBitmap(null)
        imageView.tag = cacheKey

        executor.execute {
            val bitmap = decodeThumbnail(file)
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
                mainHandler.post {
                    if (imageView.tag == cacheKey) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } else {
                Log.w(TAG, "Failed decoding thumbnail for: ${file.name}")
            }
        }
    }

    private fun decodeThumbnail(file: File): Bitmap? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val sampleSize = calculateInSampleSize(
                        info.size.width,
                        info.size.height,
                        TARGET_SIZE_PX,
                        TARGET_SIZE_PX
                    )
                    decoder.setTargetSampleSize(sampleSize)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                options.inSampleSize = calculateInSampleSize(
                    options.outWidth,
                    options.outHeight,
                    TARGET_SIZE_PX,
                    TARGET_SIZE_PX
                )
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.RGB_565
                BitmapFactory.decodeFile(file.absolutePath, options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding GIF thumbnail for ${file.name}", e)
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
