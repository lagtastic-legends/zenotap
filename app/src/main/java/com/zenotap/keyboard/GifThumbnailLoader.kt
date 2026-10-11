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
import android.view.View
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/**
 * GifThumbnailLoader
 *
 * High-performance, zero-crash media and GIF loader engineered for Android IMEs.
 * - Hardware-accelerated animated playback via ImageDecoder on API 28+
 * - Fast first-frame Bitmap LRU caching for instant flicker-free card binding
 * - Multi-view safe animation lifecycle with window attachment listeners
 * - Resilient BitmapFactory fallback for older devices or malformed streams
 */
object GifThumbnailLoader {

    private const val TAG = "GifThumbnailLoader"

    // 16MB cache for static thumbnail bitmaps
    private val maxCacheSize = (16 * 1024 * 1024).toInt()

    private val thumbnailCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxCacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadMedia(file: File, imageView: ImageView) {
        val cacheKey = "${file.absolutePath}_${file.lastModified()}"

        // Check if static thumbnail is already cached for instant visual feedback
        val cachedThumb = thumbnailCache.get(cacheKey)
        if (cachedThumb != null) {
            imageView.setImageBitmap(cachedThumb)
        } else {
            imageView.setImageDrawable(null)
        }
        imageView.tag = cacheKey

        executor.execute {
            val context = imageView.context.applicationContext
            val drawable = decodeMedia(file, context)

            if (drawable != null) {
                // If it's a static BitmapDrawable, cache its bitmap
                if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    thumbnailCache.put(cacheKey, drawable.bitmap)
                }

                mainHandler.post {
                    if (imageView.tag == cacheKey) {
                        try {
                            imageView.setImageDrawable(drawable)
                            startAnimationSafely(drawable, imageView, cacheKey)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error binding drawable to view", t)
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
        val cachedThumb = thumbnailCache.get(cacheKey)
        if (cachedThumb != null) {
            imageView.setImageBitmap(cachedThumb)
        } else {
            imageView.setImageDrawable(null)
        }
        imageView.tag = cacheKey

        executor.execute {
            val appContext = context.applicationContext
            val drawable = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(appContext.contentResolver, uri)
                    ImageDecoder.decodeDrawable(source)
                } else {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) BitmapDrawable(appContext.resources, bitmap) else null
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed decoding media from URI: $uri", e)
                null
            }

            if (drawable != null) {
                if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    thumbnailCache.put(cacheKey, drawable.bitmap)
                }
                mainHandler.post {
                    if (imageView.tag == cacheKey) {
                        try {
                            imageView.setImageDrawable(drawable)
                            startAnimationSafely(drawable, imageView, cacheKey)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error setting URI drawable", t)
                        }
                    }
                }
            }
        }
    }

    private fun decodeMedia(file: File, context: Context): Drawable? {
        if (!file.exists() || !file.canRead() || file.length() == 0L) return null

        // Try ImageDecoder on Android P+ for live animated playback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(file)
                return ImageDecoder.decodeDrawable(source)
            } catch (t: Throwable) {
                Log.w(TAG, "ImageDecoder failed for ${file.name}, trying BitmapFactory: ${t.message}")
            }
        }

        // Resilient fallback: Decode first frame with BitmapFactory
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, 240, 240)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            if (bitmap != null) {
                BitmapDrawable(context.resources, bitmap)
            } else null
        } catch (e: Throwable) {
            Log.e(TAG, "BitmapFactory fallback also failed for ${file.name}", e)
            null
        }
    }

    private fun startAnimationSafely(drawable: Drawable, view: ImageView, expectedTag: String) {
        if (drawable !is Animatable) return
        try {
            if (view.isAttachedToWindow) {
                if (!drawable.isRunning) {
                    drawable.start()
                }
            } else {
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        try {
                            if (view.tag == expectedTag && view.drawable == drawable && !drawable.isRunning) {
                                drawable.start()
                            }
                        } catch (ignored: Throwable) {}
                        view.removeOnAttachStateChangeListener(this)
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        try {
                            if (drawable.isRunning) {
                                drawable.stop()
                            }
                        } catch (ignored: Throwable) {}
                    }
                })
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Safe anim start failed", e)
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

    fun getMimeType(file: File): String {
        return if (file.name.endsWith(".webp", ignoreCase = true)) {
            "image/webp"
        } else {
            "image/gif"
        }
    }

    fun clearCache() {
        thumbnailCache.evictAll()
    }
}
