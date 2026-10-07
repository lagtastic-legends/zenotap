package com.zenotap.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zenotap.keyboard.sync.ZenoTapSyncClient
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvDeckCount: TextView
    private lateinit var tvSyncStatus: TextView
    private lateinit var tvSyncDetails: TextView
    private lateinit var btnPairZenoDeck: Button
    private lateinit var btnSyncNow: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_ime_status)
        tvDeckCount = findViewById(R.id.tv_deck_count)
        tvSyncStatus = findViewById(R.id.tv_sync_status)
        tvSyncDetails = findViewById(R.id.tv_sync_details)

        val btnEnableIme = findViewById<Button>(R.id.btn_enable_ime)
        val btnSelectIme = findViewById<Button>(R.id.btn_select_ime)
        val btnRefresh = findViewById<Button>(R.id.btn_refresh_status)
        val btnAddSample = findViewById<Button>(R.id.btn_add_sample)
        btnPairZenoDeck = findViewById(R.id.btn_pair_zenodeck)
        btnSyncNow = findViewById(R.id.btn_sync_now)

        btnEnableIme.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        btnSelectIme.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }

        btnRefresh.setOnClickListener {
            checkStatus()
        }

        btnAddSample.setOnClickListener {
            DeckStorageManager.ensureStarterPack(this)
            checkStatus()
        }

        val etTestInput = findViewById<EditText>(R.id.et_test_input)
        val btnPopupKeyboard = findViewById<Button>(R.id.btn_popup_keyboard)
        val layoutTestResult = findViewById<View>(R.id.layout_test_result)
        val tvTestResult = findViewById<TextView>(R.id.tv_test_result)
        val ivTestPreview = findViewById<ImageView>(R.id.iv_test_preview)

        // Enable Rich Content (GIF) Ingestion on the test field!
        androidx.core.view.ViewCompat.setOnReceiveContentListener(
            etTestInput,
            arrayOf("image/gif", "image/*")
        ) { _, payload ->
            val split = payload.partition { item -> item.uri != null }
            val uriContent = split.first
            val remaining = split.second

            if (uriContent != null) {
                val clip = uriContent.clip
                if (clip.itemCount > 0) {
                    val uri = clip.getItemAt(0).uri
                    if (uri != null) {
                        layoutTestResult.visibility = View.VISIBLE
                        tvTestResult.text = "🎉 Successfully received & injected GIF!"
                        GifThumbnailLoader.loadMediaFromUri(this, uri, ivTestPreview)
                        Toast.makeText(this, "🎉 GIF Received Successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            remaining
        }

        btnPopupKeyboard.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
            val isSelected = currentIme.contains(packageName)

            if (!isSelected) {
                Toast.makeText(
                    this,
                    "ZenoTap is not selected as your keyboard yet. Please select it in the dialog:",
                    Toast.LENGTH_LONG
                ).show()
                imm?.showInputMethodPicker()
            } else {
                etTestInput.requestFocus()
                imm?.showSoftInput(etTestInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        etTestInput.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
            if (!currentIme.contains(packageName)) {
                imm?.showInputMethodPicker()
            }
        }

        btnPairZenoDeck.setOnClickListener {
            showPairDialog()
        }

        btnSyncNow.setOnClickListener {
            triggerSync()
        }

        // Initialize starter reactions
        DeckStorageManager.ensureStarterPack(this)
    }

    override fun onResume() {
        super.onResume()
        checkStatus()
    }

    private fun checkStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabledList = imm?.enabledInputMethodList ?: emptyList()
        val isEnabled = enabledList.any { it.packageName == packageName }

        val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val isSelected = currentIme.contains(packageName)

        val files = DeckStorageManager.getDeckFiles(this)
        tvDeckCount.text = "Media Deck: ${files.size} Reactions Loaded"

        tvStatus.text = when {
            isSelected -> "✓ Active Default Keyboard"
            isEnabled -> "✓ Enabled (Tap Step 2 to select)"
            else -> "✗ Disabled (Tap Step 1 to enable)"
        }

        tvStatus.setTextColor(
            if (isSelected) 0xFF10B981.toInt()
            else if (isEnabled) 0xFF3B82F6.toInt()
            else 0xFFF59E0B.toInt()
        )

        // Cloud sync status
        val isPaired = ZenoTapSyncClient.isPaired(this)
        if (isPaired) {
            val device = ZenoTapSyncClient.getDeviceName(this)
            tvSyncStatus.text = "Linked"
            tvSyncStatus.setTextColor(0xFF10B981.toInt())
            tvSyncDetails.text = "Linked to ZenoDeck cloud deck ($device). Sync token is secured."
            btnPairZenoDeck.text = "Unlink Account"
            btnSyncNow.isEnabled = true
        } else {
            tvSyncStatus.text = "Not Linked"
            tvSyncStatus.setTextColor(0xFFF59E0B.toInt())
            tvSyncDetails.text = "Pair with ZenoDeck to automatically sync your custom reaction GIFs."
            btnPairZenoDeck.text = "Link Account (6-Digit Code)"
            btnSyncNow.isEnabled = false
        }
    }

    private fun showPairDialog() {
        if (ZenoTapSyncClient.isPaired(this)) {
            AlertDialog.Builder(this)
                .setTitle("Unlink ZenoDeck Account?")
                .setMessage("This will remove your sync token from this device. Local GIFs will remain saved.")
                .setPositiveButton("Unlink") { _, _ ->
                    ZenoTapSyncClient.unpair(this)
                    checkStatus()
                    Toast.makeText(this, "Device unlinked", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val input = EditText(this).apply {
            hint = "6-digit code (e.g. 849201)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Link ZenoDeck Account")
            .setMessage("Enter the 6-digit pairing code shown in your ZenoDeck Web App:")
            .setView(input)
            .setPositiveButton("Link Device") { _, _ ->
                val code = input.text.toString().trim()
                if (code.length != 6) {
                    Toast.makeText(this, "Please enter a valid 6-digit code", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                Toast.makeText(this, "Pairing device...", Toast.LENGTH_SHORT).show()
                thread {
                    val result = ZenoTapSyncClient.pairDevice(this, ZenoTapSyncClient.DEFAULT_SERVER_URL, code)
                    runOnUiThread {
                        result.onSuccess {
                            Toast.makeText(this, "Successfully paired to ZenoDeck!", Toast.LENGTH_SHORT).show()
                            checkStatus()
                            triggerSync()
                        }.onFailure { err ->
                            Toast.makeText(this, "Pairing failed: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun triggerSync() {
        Toast.makeText(this, "Syncing cloud deck...", Toast.LENGTH_SHORT).show()
        thread {
            val result = ZenoTapSyncClient.syncDeck(this)
            runOnUiThread {
                result.onSuccess { count ->
                    Toast.makeText(this, "Sync complete! $count new GIFs downloaded.", Toast.LENGTH_SHORT).show()
                    checkStatus()
                }.onFailure { err ->
                    Toast.makeText(this, "Sync error: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
