package com.zenotap.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvDeckCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_ime_status)
        tvDeckCount = findViewById(R.id.tv_deck_count)

        val btnEnableIme = findViewById<Button>(R.id.btn_enable_ime)
        val btnSelectIme = findViewById<Button>(R.id.btn_select_ime)
        val btnRefresh = findViewById<Button>(R.id.btn_refresh_status)
        val btnAddSample = findViewById<Button>(R.id.btn_add_sample)

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
    }
}
