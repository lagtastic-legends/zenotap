package com.zenotap.keyboard

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * ZenoTapKeyboardService
 *
 * Dedicated Android InputMethodService (IME) for 1-tap rich media & GIF injection.
 */
class ZenoTapKeyboardService : InputMethodService() {

    companion object {
        const val TAG = "ZenoTapIME"
        const val MIME_TYPE_GIF = "image/gif"
        const val MIME_TYPE_IMAGE_ANY = "image/*"
    }

    private var keyboardRootView: View? = null
    private var statusTextView: TextView? = null
    private var richContentBadge: TextView? = null
    private var commitResultTextView: TextView? = null
    private var deckTitleTextView: TextView? = null
    private var rvGifDeck: RecyclerView? = null
    private var layoutEmptyDeck: View? = null
    private var gifAdapter: GifDeckAdapter? = null

    private var currentEditorInfo: EditorInfo? = null
    private var isRichContentSupportedByHost: Boolean = false

    private val deckGifs = mutableListOf<File>()

    private val deckUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Deck update broadcast received. Refreshing grid.")
            refreshDeckFiles()
        }
    }
    private var isReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ZenoTapKeyboardService created.")

        // Ensure starter reactions exist
        DeckStorageManager.ensureStarterPack(this)

        val filter = IntentFilter(DeckStorageManager.ACTION_DECK_UPDATED)
        try {
            ContextCompat.registerReceiver(
                this,
                deckUpdateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering deckUpdateReceiver", e)
        }
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardRootView = view
        bindKeyboardViews(view)
        refreshDeckFiles()
        return view
    }

    private fun bindKeyboardViews(root: View) {
        statusTextView = root.findViewById(R.id.tv_keyboard_status)
        richContentBadge = root.findViewById(R.id.tv_rich_content_badge)
        commitResultTextView = root.findViewById(R.id.tv_commit_result)
        deckTitleTextView = root.findViewById(R.id.tv_deck_title)
        rvGifDeck = root.findViewById(R.id.rv_gif_deck)
        layoutEmptyDeck = root.findViewById(R.id.layout_empty_deck)

        val btnSwitchIme = root.findViewById<ImageButton>(R.id.btn_switch_ime)
        val btnSwitchInput = root.findViewById<Button>(R.id.btn_switch_input)
        val btnClose = root.findViewById<ImageButton>(R.id.btn_close_keyboard)
        val btnOpenApp = root.findViewById<Button>(R.id.btn_open_app)
        val btnCreateSample = root.findViewById<Button>(R.id.btn_create_sample)

        gifAdapter = GifDeckAdapter(
            onItemClick = { file ->
                commitGif(file, description = file.nameWithoutExtension)
            },
            onItemLongClick = { file ->
                Toast.makeText(this@ZenoTapKeyboardService, "Selected: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        )

        rvGifDeck?.apply {
            layoutManager = GridLayoutManager(this@ZenoTapKeyboardService, 3)
            adapter = gifAdapter
            setHasFixedSize(true)
        }

        val switchAction = View.OnClickListener {
            switchToNextOrPicker()
        }

        btnSwitchIme?.setOnClickListener(switchAction)
        btnSwitchInput?.setOnClickListener(switchAction)

        btnClose?.setOnClickListener {
            requestHideSelf(0)
        }

        btnOpenApp?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(intent)
        }

        btnCreateSample?.setOnClickListener {
            DeckStorageManager.ensureStarterPack(this)
            refreshDeckFiles()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentEditorInfo = info

        refreshDeckFiles()

        if (info == null) {
            updateHostCapabilitiesDisplay("Unknown Host", supported = false, emptyMime = true)
            return
        }

        val targetPackage = info.packageName ?: "Host App"
        val supportedMimes = EditorInfoCompat.getContentMimeTypes(info)
        isRichContentSupportedByHost = isMimeSupported(supportedMimes, MIME_TYPE_GIF)

        Log.d(TAG, "Host package: $targetPackage | richContent: $isRichContentSupportedByHost")
        updateHostCapabilitiesDisplay(targetPackage, isRichContentSupportedByHost, supportedMimes.isEmpty())
        updateCommitResultDisplay("Ready for media injection", isSuccess = null)
    }

    fun refreshDeckFiles(): List<File> {
        val files = DeckStorageManager.getDeckFiles(this)

        deckGifs.clear()
        deckGifs.addAll(files)

        val count = deckGifs.size
        deckTitleTextView?.text = "⚡ ZenoTap Deck ($count)"

        if (count == 0) {
            rvGifDeck?.visibility = View.GONE
            layoutEmptyDeck?.visibility = View.VISIBLE
        } else {
            rvGifDeck?.visibility = View.VISIBLE
            layoutEmptyDeck?.visibility = View.GONE
            gifAdapter?.submitList(deckGifs.toList())
        }

        return deckGifs.toList()
    }

    private fun isMimeSupported(supportedMimes: Array<String>, targetMime: String): Boolean {
        return supportedMimes.any { mime ->
            mime.equals(targetMime, ignoreCase = true) ||
            mime.equals(MIME_TYPE_IMAGE_ANY, ignoreCase = true) ||
            (targetMime.startsWith("image/") && mime.startsWith("image/*", ignoreCase = true))
        }
    }

    fun commitGif(
        gifFile: File,
        linkUri: Uri? = null,
        description: String = "ZenoTap GIF"
    ): Boolean {
        if (!gifFile.exists() || !gifFile.canRead()) {
            updateCommitResultDisplay("Error reading file", isSuccess = false)
            return false
        }

        val inputConnection: InputConnection? = currentInputConnection
        val editorInfo: EditorInfo? = currentEditorInfo ?: currentInputEditorInfo

        if (inputConnection == null || editorInfo == null) {
            updateCommitResultDisplay("No active input target", isSuccess = false)
            return false
        }

        val targetPackage = editorInfo.packageName ?: "Host App"
        val supportedMimes = EditorInfoCompat.getContentMimeTypes(editorInfo)

        if (!isMimeSupported(supportedMimes, MIME_TYPE_GIF)) {
            Log.w(TAG, "Host ($targetPackage) lacks GIF support. Triggering fallback.")
            executeFallback(gifFile, null, linkUri)
            return false
        }

        val contentUri: Uri = try {
            FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                gifFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider error", e)
            executeFallback(gifFile, null, linkUri)
            return false
        }

        val clipDescription = ClipDescription(description, arrayOf(MIME_TYPE_GIF))
        val inputContentInfo = InputContentInfoCompat(contentUri, clipDescription, linkUri)

        var flags = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            flags = flags or InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        }

        return try {
            val success = InputConnectionCompat.commitContent(
                inputConnection,
                editorInfo,
                inputContentInfo,
                flags,
                null
            )

            if (success) {
                updateCommitResultDisplay("✓ Injected into $targetPackage", isSuccess = true)
                true
            } else {
                executeFallback(gifFile, contentUri, linkUri)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "CommitContent exception", e)
            executeFallback(gifFile, contentUri, linkUri)
            false
        }
    }

    private fun executeFallback(
        gifFile: File,
        resolvedUri: Uri?,
        linkUri: Uri?
    ) {
        try {
            val contentUri = resolvedUri ?: FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                gifFile
            )

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clipData = ClipData.newUri(contentResolver, "ZenoTap GIF", contentUri)
                clipboard.setPrimaryClip(clipData)
            }

            if (linkUri != null) {
                currentInputConnection?.commitText(linkUri.toString(), 1)
            }

            Toast.makeText(this, "GIF copied to clipboard — paste into chat!", Toast.LENGTH_SHORT).show()
            updateCommitResultDisplay("⚠ Incompatible app: Copied to clipboard", isSuccess = false)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback failed", e)
            updateCommitResultDisplay("Fallback error: ${e.message}", isSuccess = false)
        }
    }

    private fun updateHostCapabilitiesDisplay(hostPackage: String, supported: Boolean, emptyMime: Boolean) {
        statusTextView?.text = when {
            supported -> "Target: $hostPackage (GIF Supported)"
            emptyMime -> "Target: $hostPackage (Standard Text Field)"
            else -> "Target: $hostPackage (Rich Content Restricted)"
        }

        richContentBadge?.apply {
            if (supported) {
                text = "⚡ CommitContent"
                setTextColor(0xFF10B981.toInt())
            } else {
                text = "⚠️ Fallback"
                setTextColor(0xFFF59E0B.toInt())
            }
        }
    }

    private fun updateCommitResultDisplay(text: String, isSuccess: Boolean?) {
        commitResultTextView?.apply {
            this.text = text
            when (isSuccess) {
                true -> setTextColor(0xFF10B981.toInt())
                false -> setTextColor(0xFFF87171.toInt())
                null -> setTextColor(0xFF9CA3AF.toInt())
            }
        }
    }

    private fun switchToNextOrPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!switchToPreviousInputMethod()) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            }
        } else {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        GifThumbnailLoader.clearCache()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        currentEditorInfo = null
    }

    override fun onDestroy() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(deckUpdateReceiver)
            } catch (ignored: Exception) {}
            isReceiverRegistered = false
        }
        GifThumbnailLoader.clearCache()
        keyboardRootView = null
        statusTextView = null
        richContentBadge = null
        commitResultTextView = null
        deckTitleTextView = null
        rvGifDeck = null
        layoutEmptyDeck = null
        gifAdapter = null
        currentEditorInfo = null
        deckGifs.clear()
        super.onDestroy()
        Log.i(TAG, "ZenoTapKeyboardService destroyed.")
    }
}
