package com.zenotap.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
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
 * High-performance, zero-dependency animated GIF & media loader
 * with native hardware-accelerated playback (AnimatedImageDrawable on API 28+),
 * automatic BitmapFactory fallback, and memory-bounded LRU caching.
 */
object GifThumbnailLoader {

    private const val TAG = "GifThumbnailLoader"

    // 20MB cache capacity
    private val maxCacheSize = (20 * 1024 * 1024).toInt()

    private val memoryCache: LruCache<String, Drawable> = object : LruCache<String, Drawable>(maxCacheSize) {
        override fun sizeOf(key: String, drawable: Drawable): Int {
            return when (drawable) {
                is BitmapDrawable -> drawable.bitmap?.byteCount ?: 1024
                else -> 200 * 200 * 4 // Approximation for animated drawables
            }
        }
    }

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadMedia(file: File, imageView: ImageView) {
        val cacheKey = "${file.absolutePath}_${file.lastModified()}"

        // Check memory cache first
        val cached = memoryCache.get(cacheKey)
        if (cached != null) {
            imageView.setImageDrawable(cached)
            if (cached is Animatable && !cached.isRunning) {
                cached.start()
            }
            return
        }

        imageView.setImageDrawable(null)
        imageView.tag = cacheKey

        executor.execute {
            val drawable = decodeMedia(file, imageView)
            if (drawable != null) {
                memoryCache.put(cacheKey, drawable)
                mainHandler.post {
                    if (imageView.tag == cacheKey) {
                        imageView.setImageDrawable(drawable)
                        if (drawable is Animatable && !drawable.isRunning) {
                            drawable.start()
                        }
                    }
                }
            } else {
                Log.w(TAG, "Failed decoding media for: ${file.name}")
            }
        }
    }

    fun loadMediaFromUri(context: Context, uri: Uri, imageView: ImageView) {
        val cacheKey = uri.toString()
        val cached = memoryCache.get(cacheKey)
        if (cached != null) {
            imageView.setImageDrawable(cached)
            if (cached is Animatable && !cached.isRunning) {
                cached.start()
            }
            return
        }

        imageView.setImageDrawable(null)
        imageView.tag = cacheKey

        executor.execute {
            val drawable = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeDrawable(source)
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) BitmapDrawable(context.resources, bitmap) else null
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed decoding media from URI: $uri", e)
                null
            }

            if (drawable != null) {
                memoryCache.put(cacheKey, drawable)
                mainHandler.post {
                    if (imageView.tag == cacheKey) {
                        imageView.setImageDrawable(drawable)
                        if (drawable is Animatable && !drawable.isRunning) {
                            drawable.start()
                        }
                    }
                }
            }
        }
    }

    private fun decodeMedia(file: File, targetView: ImageView): Drawable? {
        if (!file.exists() || !file.canRead()) return null

        // Try ImageDecoder on Android P+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(file)
                val drawable = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                return drawable
            } catch (t: Throwable) {
                Log.w(TAG, "ImageDecoder failed for ${file.name}, trying BitmapFactory fallback", t)
            }
        }

        // Resilient fallback: Decode first frame with BitmapFactory
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, 200, 200)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            if (bitmap != null) {
                BitmapDrawable(targetView.resources, bitmap)
            } else null
        } catch (e: Throwable) {
            Log.e(TAG, "BitmapFactory fallback also failed for ${file.name}", e)
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
