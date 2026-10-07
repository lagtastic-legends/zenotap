package com.zenotap.keyboard.sync

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.zenotap.keyboard.DeckStorageManager
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ZenoTapSyncClient {

    private const val PREFS_NAME = "zenotap_sync_prefs"
    private const val KEY_SYNC_TOKEN = "sync_token"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_DEVICE_NAME = "device_name"

    const val DEFAULT_SERVER_URL = "http://10.0.2.2:3000"

    fun isPaired(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_SYNC_TOKEN, null).isNullOrBlank()
    }

    fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun getDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_NAME, "Android Device") ?: "Android Device"
    }

    fun unpair(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /**
     * Confirms a 6-digit pair code with the ZenoTap backend and stores the scoped sync token.
     */
    fun pairDevice(context: Context, serverUrl: String, pairCode: String): Result<String> {
        val cleanUrl = serverUrl.trimEnd('/')
        val endpoint = "$cleanUrl/api/zenotap/v1/device/pair/"

        return try {
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true

            val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            val payload = JSONObject().apply {
                put("pairCode", pairCode.trim())
                put("deviceName", deviceName)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val statusCode = conn.responseCode
            val responseStream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
            val responseBody = readStreamToString(responseStream)

            if (statusCode in 200..299) {
                val json = JSONObject(responseBody)
                val syncToken = json.getString("syncToken")
                val confirmedDevice = json.optString("deviceName", deviceName)

                // Persist token in secure private SharedPreferences
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_SYNC_TOKEN, syncToken)
                    .putString(KEY_SERVER_URL, cleanUrl)
                    .putString(KEY_DEVICE_NAME, confirmedDevice)
                    .apply()

                Result.success(confirmedDevice)
            } else {
                val errJson = try { JSONObject(responseBody) } catch (e: Exception) { null }
                val errMsg = errJson?.optString("error") ?: "Pairing failed with status $statusCode"
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches user's active cloud GIF deck and downloads missing files to local keyboard storage.
     */
    fun syncDeck(context: Context): Result<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val syncToken = prefs.getString(KEY_SYNC_TOKEN, null)
            ?: return Result.failure(IllegalStateException("Device is not paired to a ZenoDeck account."))
        val serverUrl = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

        val endpoint = "${serverUrl.trimEnd('/')}/api/zenotap/v1/sync/"

        return try {
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $syncToken")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000

            val statusCode = conn.responseCode
            val responseStream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
            val responseBody = readStreamToString(responseStream)

            if (statusCode !in 200..299) {
                val errJson = try { JSONObject(responseBody) } catch (e: Exception) { null }
                val errMsg = errJson?.optString("error") ?: "Sync request failed ($statusCode)"
                return Result.failure(Exception(errMsg))
            }

            val json = JSONObject(responseBody)
            val deckArray = json.getJSONArray("deck")

            val existingFiles = DeckStorageManager.getDeckFiles(context).map { it.name }.toSet()
            var downloadedCount = 0

            for (i in 0 until deckArray.length()) {
                val item = deckArray.getJSONObject(i)
                val filename = item.getString("filename")
                val downloadUrl = item.getString("downloadUrl")

                if (!existingFiles.contains(filename)) {
                    // Download file bytes
                    val bytes = downloadFileBytes(downloadUrl)
                    if (bytes != null && bytes.isNotEmpty()) {
                        DeckStorageManager.addGif(context, filename, bytes)
                        downloadedCount++
                    }
                }
            }

            // Emit broadcast so the active keyboard view refreshes immediately
            context.sendBroadcast(Intent(DeckStorageManager.ACTION_DECK_UPDATED).apply {
                setPackage(context.packageName)
            })

            Result.success(downloadedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Runs syncDeck on a background thread and posts the result to the main thread.
     */
    fun syncDeckAsync(context: Context, onComplete: ((Result<Int>) -> Unit)? = null) {
        val handler = Handler(Looper.getMainLooper())
        kotlin.concurrent.thread(start = true) {
            val res = syncDeck(context)
            handler.post {
                onComplete?.invoke(res)
            }
        }
    }

    private fun downloadFileBytes(fileUrl: String): ByteArray? {
        return try {
            val url = URL(fileUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { readStreamToBytes(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readStreamToString(stream: InputStream): String {
        return stream.bufferedReader().use { it.readText() }
    }

    private fun readStreamToBytes(stream: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        BufferedInputStream(stream).use { bis ->
            val data = ByteArray(8192)
            var count: Int
            while (bis.read(data, 0, data.size).also { count = it } != -1) {
                buffer.write(data, 0, count)
            }
        }
        return buffer.toByteArray()
    }
}
