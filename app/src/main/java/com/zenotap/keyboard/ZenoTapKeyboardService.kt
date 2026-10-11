package com.zenotap.keyboard

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zenotap.keyboard.sync.ZenoTapSyncClient
import java.io.File

/**
 * ZenoTapKeyboardService
 *
 * Ultra-stable, high-performance Android InputMethodService (IME) for 1-tap rich media & GIF/WebP injection.
 * Features in-keyboard instant search, Favorites & Recents engine, long-press contextual actions,
 * Obsidian Cyber UI styling, and multi-layer crash-proofing across all Android devices.
 */
class ZenoTapKeyboardService : InputMethodService() {

    companion object {
        const val TAG = "ZenoTapIME"
        const val MIME_TYPE_GIF = "image/gif"
        const val MIME_TYPE_WEBP = "image/webp"
        const val MIME_TYPE_IMAGE_ANY = "image/*"
    }

    private var keyboardRootView: View? = null
    private var rvGifDeck: RecyclerView? = null
    private var layoutEmptyDeck: View? = null
    private var gifAdapter: GifDeckAdapter? = null
    private var feedbackToast: TextView? = null

    // Search bar UI elements
    private var layoutToolbarDefault: View? = null
    private var layoutToolbarSearch: View? = null
    private var etSearchReactions: EditText? = null
    private var btnClearSearch: ImageButton? = null

    private var currentEditorInfo: EditorInfo? = null
    private var isRichContentSupportedByHost: Boolean = false

    private val deckGifs = mutableListOf<File>()
    private var activeFilterChip: TextView? = null
    private val allFilterChips = mutableListOf<TextView>()

    private val hideToastRunnable = Runnable {
        feedbackToast?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
            feedbackToast?.visibility = View.GONE
        }?.start()
    }

    private val deckUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Deck update broadcast received. Refreshing grid.")
            try {
                refreshDeckFiles()
            } catch (e: Throwable) {
                Log.e(TAG, "Error handling deck update broadcast", e)
            }
        }
    }
    private var isReceiverRegistered = false

    override fun onCreate() {
        // Enforce application theme on Service base context
        setTheme(R.style.Theme_ZenoTap)
        super.onCreate()
        Log.i(TAG, "ZenoTapKeyboardService created.")

        // Ensure starter reactions exist and auto-upgrade legacy placeholders
        try {
            DeckStorageManager.ensureStarterPack(this)
        } catch (e: Throwable) {
            Log.e(TAG, "Error ensuring starter pack", e)
        }

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
        return try {
            val themedContext = ContextThemeWrapper(this, R.style.Theme_ZenoTap)
            val themedInflater = LayoutInflater.from(themedContext)
            val view = themedInflater.inflate(R.layout.keyboard_view, null)
            val heightPx = (275 * resources.displayMetrics.density).toInt()
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx
            )
            keyboardRootView = view
            bindKeyboardViews(view, themedContext)
            refreshDeckFiles()
            view
        } catch (e: Throwable) {
            Log.e(TAG, "Critical error inflating keyboard view. Activating emergency layout.", e)
            createEmergencyFallbackView()
        }
    }

    private fun createEmergencyFallbackView(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F1015"))
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (275 * resources.displayMetrics.density).toInt()
            )
        }

        val tv = TextView(this).apply {
            text = "⚡ ZenoTap Keyboard"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        val btnRetry = Button(this).apply {
            text = "Reload Keyboard"
            setOnClickListener {
                setInputView(onCreateInputView())
            }
        }
        val btnSwitch = Button(this).apply {
            text = "Switch to QWERTY"
            setOnClickListener {
                switchToNextOrPicker()
            }
        }

        layout.addView(tv)
        layout.addView(btnRetry)
        layout.addView(btnSwitch)
        return layout
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        if (outInsets == null) return
        try {
            val root = keyboardRootView
            if (root != null && root.isShown && root.height > 0) {
                val loc = IntArray(2)
                root.getLocationInWindow(loc)
                val topY = loc[1]
                if (topY > 0) {
                    outInsets.contentTopInsets = topY
                    outInsets.visibleTopInsets = topY
                    outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Safe onComputeInsets fallback", e)
        }
    }

    private fun bindKeyboardViews(root: View, themedContext: Context) {
        rvGifDeck = root.findViewById(R.id.rv_gif_deck)
        layoutEmptyDeck = root.findViewById(R.id.layout_empty_deck)
        feedbackToast = root.findViewById(R.id.tv_feedback_toast)

        layoutToolbarDefault = root.findViewById(R.id.layout_toolbar_default)
        layoutToolbarSearch = root.findViewById(R.id.layout_toolbar_search)
        etSearchReactions = root.findViewById(R.id.et_search_reactions)
        btnClearSearch = root.findViewById(R.id.btn_clear_search)

        val btnToggleSearch = root.findViewById<ImageButton>(R.id.btn_toggle_search)
        val btnCloseSearch = root.findViewById<ImageButton>(R.id.btn_close_search)
        val btnSyncDeck = root.findViewById<ImageButton>(R.id.btn_sync_deck)
        val btnSwitchIme = root.findViewById<ImageButton>(R.id.btn_switch_ime)
        val btnClose = root.findViewById<ImageButton>(R.id.btn_close_keyboard)
        val btnManageDeck = root.findViewById<Button>(R.id.btn_manage_deck)
        val btnSwitchInput = root.findViewById<Button>(R.id.btn_switch_input)
        val btnResetStarter = root.findViewById<Button>(R.id.btn_create_sample)

        gifAdapter = GifDeckAdapter(
            onItemClick = { file ->
                keyboardRootView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                DeckStorageManager.recordRecent(this, file)
                commitGif(file, description = file.nameWithoutExtension)
            },
            onItemLongClick = { file ->
                showReactionContextMenu(file, themedContext)
            }
        )

        rvGifDeck?.apply {
            layoutManager = GridLayoutManager(themedContext, 3)
            adapter = gifAdapter
            setHasFixedSize(true)
        }

        // Setup Category Filter Chips (including Favs and Recent)
        allFilterChips.clear()
        val chipFavs = root.findViewById<TextView>(R.id.chip_favs)
        val chipRecent = root.findViewById<TextView>(R.id.chip_recent)
        val chipAll = root.findViewById<TextView>(R.id.chip_all)
        val chipFire = root.findViewById<TextView>(R.id.chip_fire)
        val chipHype = root.findViewById<TextView>(R.id.chip_hype)
        val chipVibe = root.findViewById<TextView>(R.id.chip_vibe)
        val chipLol = root.findViewById<TextView>(R.id.chip_lol)
        val chipLove = root.findViewById<TextView>(R.id.chip_love)

        val chipsWithCategories = listOfNotNull(
            chipFavs?.let { it to "Favs" },
            chipRecent?.let { it to "Recent" },
            chipAll?.let { it to "All" },
            chipFire?.let { it to "Fire" },
            chipHype?.let { it to "Hype" },
            chipVibe?.let { it to "Vibe" },
            chipLol?.let { it to "LOL" },
            chipLove?.let { it to "Love" }
        )

        allFilterChips.addAll(chipsWithCategories.map { it.first })
        activeFilterChip = chipAll

        for ((chip, category) in chipsWithCategories) {
            chip.setOnClickListener {
                selectCategory(chip, category)
            }
        }

        // Search Bar Interactions
        btnToggleSearch?.setOnClickListener {
            layoutToolbarDefault?.visibility = View.GONE
            layoutToolbarSearch?.visibility = View.VISIBLE
            etSearchReactions?.requestFocus()
        }

        btnCloseSearch?.setOnClickListener {
            etSearchReactions?.text?.clear()
            gifAdapter?.setSearchQuery("")
            layoutToolbarSearch?.visibility = View.GONE
            layoutToolbarDefault?.visibility = View.VISIBLE
        }

        btnClearSearch?.setOnClickListener {
            etSearchReactions?.text?.clear()
        }

        etSearchReactions?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                btnClearSearch?.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                gifAdapter?.setSearchQuery(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Action Buttons
        val switchAction = View.OnClickListener {
            switchToNextOrPicker()
        }

        btnSwitchIme?.setOnClickListener(switchAction)
        btnSwitchInput?.setOnClickListener(switchAction)

        btnClose?.setOnClickListener {
            requestHideSelf(0)
        }

        btnManageDeck?.setOnClickListener {
            try {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                startActivity(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed launching MainActivity", e)
            }
        }

        btnSyncDeck?.setOnClickListener {
            btnSyncDeck.animate().rotationBy(360f).setDuration(600).start()
            showFeedbackToast("🔄 Syncing with ZenoDeck...", isSuccess = true)
            ZenoTapSyncClient.syncDeckAsync(this) { result ->
                result.onSuccess { count ->
                    showFeedbackToast("✓ Synced $count new reactions! 🚀", isSuccess = true)
                    refreshDeckFiles()
                }.onFailure { err ->
                    showFeedbackToast(err.message ?: "Sync failed", isSuccess = false)
                }
            }
        }

        btnResetStarter?.setOnClickListener {
            try {
                DeckStorageManager.ensureStarterPack(this)
                refreshDeckFiles()
                showFeedbackToast("✨ Starter reactions restored", isSuccess = true)
            } catch (e: Throwable) {
                Log.e(TAG, "Error restoring starter pack", e)
            }
        }
    }

    private fun showReactionContextMenu(file: File, themedContext: Context) {
        val isFav = DeckStorageManager.isFavorite(this, file)
        val favLabel = if (isFav) "⭐ Remove from Favorites" else "⭐ Add to Favorites"
        val sizeKb = (file.length() / 1024).coerceAtLeast(1)
        val format = if (file.name.endsWith(".webp", ignoreCase = true)) "WebP" else "GIF"

        val options = arrayOf(
            favLabel,
            "🗑️ Delete Reaction",
            "ℹ️ Info ($format, ${sizeKb}KB)"
        )

        AlertDialog.Builder(themedContext)
            .setTitle(DeckStorageManager.getDisplayTitleForFile(file))
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // Toggle Favorite
                        val nowFav = DeckStorageManager.toggleFavorite(this, file)
                        showFeedbackToast(if (nowFav) "⭐ Added to Favorites!" else "Removed from Favorites", isSuccess = true)
                        refreshDeckFiles()
                    }
                    1 -> { // Delete
                        val deleted = DeckStorageManager.deleteMedia(this, file)
                        if (deleted) {
                            showFeedbackToast("🗑️ Reaction deleted", isSuccess = true)
                            refreshDeckFiles()
                        } else {
                            showFeedbackToast("Unable to delete file", isSuccess = false)
                        }
                    }
                    2 -> { // Info
                        showFeedbackToast("📁 ${file.name} ($sizeKb KB)", isSuccess = true)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selectCategory(selectedChip: TextView, category: String) {
        activeFilterChip = selectedChip
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_active)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.bg_chip_inactive)
        val activeColor = ContextCompat.getColor(this, R.color.pill_active_text)
        val inactiveColor = ContextCompat.getColor(this, R.color.pill_inactive_text)

        for (chip in allFilterChips) {
            if (chip == selectedChip) {
                chip.background = activeBg
                chip.setTextColor(activeColor)
            } else {
                chip.background = inactiveBg
                chip.setTextColor(inactiveColor)
            }
        }

        when {
            category.equals("Favs", ignoreCase = true) -> {
                val favs = DeckStorageManager.getFavorites(this)
                gifAdapter?.applyCustomList(favs, "Favs")
                updateEmptyStateVisibility(favs.isEmpty())
            }
            category.equals("Recent", ignoreCase = true) -> {
                val recents = DeckStorageManager.getRecents(this)
                gifAdapter?.applyCustomList(recents, "Recent")
                updateEmptyStateVisibility(recents.isEmpty())
            }
            else -> {
                gifAdapter?.setCategory(category)
                updateEmptyStateVisibility(deckGifs.isEmpty())
            }
        }
    }

    private fun updateEmptyStateVisibility(isEmpty: Boolean) {
        if (isEmpty) {
            rvGifDeck?.visibility = View.GONE
            layoutEmptyDeck?.visibility = View.VISIBLE
        } else {
            rvGifDeck?.visibility = View.VISIBLE
            layoutEmptyDeck?.visibility = View.GONE
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentEditorInfo = info

        try {
            refreshDeckFiles()
        } catch (e: Throwable) {
            Log.e(TAG, "Error refreshing deck on start input", e)
        }

        val supportedMimes = if (info != null) EditorInfoCompat.getContentMimeTypes(info) else emptyArray()
        isRichContentSupportedByHost = isMimeSupported(supportedMimes, MIME_TYPE_GIF) || isMimeSupported(supportedMimes, MIME_TYPE_WEBP)
    }

    fun refreshDeckFiles(): List<File> {
        val files = DeckStorageManager.getDeckFiles(this)

        deckGifs.clear()
        deckGifs.addAll(files)

        val count = deckGifs.size

        if (count == 0) {
            rvGifDeck?.visibility = View.GONE
            layoutEmptyDeck?.visibility = View.VISIBLE
        } else {
            rvGifDeck?.visibility = View.VISIBLE
            layoutEmptyDeck?.visibility = View.GONE

            val currentCategory = activeFilterChip?.text?.toString() ?: "All"
            when {
                currentCategory.contains("Fav", ignoreCase = true) -> {
                    gifAdapter?.applyCustomList(DeckStorageManager.getFavorites(this), "Favs")
                }
                currentCategory.contains("Recent", ignoreCase = true) -> {
                    gifAdapter?.applyCustomList(DeckStorageManager.getRecents(this), "Recent")
                }
                else -> {
                    gifAdapter?.setMasterList(deckGifs.toList())
                }
            }
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
        description: String = "ZenoTap Reaction"
    ): Boolean {
        if (!gifFile.exists() || !gifFile.canRead()) {
            showFeedbackToast("Unable to read media file", isSuccess = false)
            return false
        }

        val inputConnection: InputConnection? = currentInputConnection
        val editorInfo: EditorInfo? = currentEditorInfo ?: currentInputEditorInfo

        if (inputConnection == null || editorInfo == null) {
            executeFallback(gifFile, null, linkUri)
            return false
        }

        val supportedMimes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        val targetMime = GifThumbnailLoader.getMimeType(gifFile)

        if (!isMimeSupported(supportedMimes, targetMime) && !isMimeSupported(supportedMimes, MIME_TYPE_GIF)) {
            Log.w(TAG, "Host lacks native $targetMime insertion support. Triggering fallback.")
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

        try {
            editorInfo.packageName?.let { hostPkg ->
                grantUriPermission(hostPkg, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Log.w(TAG, "grantUriPermission warning", e)
        }

        val clipDescription = ClipDescription(description, arrayOf(targetMime, MIME_TYPE_GIF))
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
                showFeedbackToast("✓ Sent to chat! 🚀", isSuccess = true)
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
                val clipData = ClipData.newUri(contentResolver, "ZenoTap Reaction", contentUri)
                clipboard.setPrimaryClip(clipData)
            }

            if (linkUri != null) {
                currentInputConnection?.commitText(linkUri.toString(), 1)
            }

            showFeedbackToast("📋 Copied to clipboard — paste into chat!", isSuccess = false)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback failed", e)
            showFeedbackToast("Could not copy media", isSuccess = false)
        }
    }

    private fun showFeedbackToast(message: String, isSuccess: Boolean) {
        feedbackToast?.let { toast ->
            try {
                toast.removeCallbacks(hideToastRunnable)
                toast.text = message
                val textColor = ContextCompat.getColor(
                    this,
                    if (isSuccess) R.color.accent_emerald else R.color.accent_amber
                )
                toast.setTextColor(textColor)
                toast.alpha = 0f
                toast.visibility = View.VISIBLE
                toast.animate().alpha(1f).setDuration(160).start()
                toast.postDelayed(hideToastRunnable, 2200)
            } catch (ignored: Throwable) {}
        }
    }

    private fun switchToNextOrPicker() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (!switchToPreviousInputMethod()) {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showInputMethodPicker()
                }
            } else {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error switching IME", e)
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
        rvGifDeck = null
        layoutEmptyDeck = null
        gifAdapter = null
        feedbackToast = null
        layoutToolbarDefault = null
        layoutToolbarSearch = null
        etSearchReactions = null
        btnClearSearch = null
        currentEditorInfo = null
        deckGifs.clear()
        super.onDestroy()
        Log.i(TAG, "ZenoTapKeyboardService destroyed.")
    }
}
