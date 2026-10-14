package com.zenotap.keyboard

import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale

/**
 * GifDeckAdapter
 *
 * High-performance RecyclerView adapter for binding live animated reaction cards
 * with tactile touch feedback, tag labeling, live search filtering, format badges (GIF vs STICKER),
 * favorites badge indicator, and robust lifecycle detachment/recycling safeguards.
 */
class GifDeckAdapter(
    private val onItemClick: (File) -> Unit,
    private val onItemLongClick: ((File) -> Unit)? = null
) : RecyclerView.Adapter<GifDeckAdapter.GifViewHolder>() {

    private val allItems = mutableListOf<File>()
    private val displayedItems = mutableListOf<File>()
    private var activeCategory: String = "All"
    private var searchQuery: String = ""

    fun setMasterList(files: List<File>) {
        allItems.clear()
        allItems.addAll(files)
        applyFilter()
    }

    fun setCategory(category: String) {
        if (activeCategory == category && searchQuery.isEmpty()) return
        activeCategory = category
        searchQuery = "" // Reset search when switching categories
        applyFilter()
    }

    fun setSearchQuery(query: String) {
        val clean = query.trim()
        if (searchQuery == clean) return
        searchQuery = clean
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = when {
            searchQuery.isNotBlank() -> {
                allItems.filter { file ->
                    file.name.contains(searchQuery, ignoreCase = true) ||
                    DeckStorageManager.getCategoryForFile(file).contains(searchQuery, ignoreCase = true) ||
                    DeckStorageManager.getDisplayTitleForFile(file).contains(searchQuery, ignoreCase = true)
                }
            }
            activeCategory.equals("Favs", ignoreCase = true) || activeCategory.equals("Favorites", ignoreCase = true) -> {
                allItems
            }
            activeCategory.equals("Recent", ignoreCase = true) -> {
                allItems
            }
            activeCategory.equals("All", ignoreCase = true) -> {
                allItems
            }
            else -> {
                allItems.filter { file ->
                    DeckStorageManager.getCategoryForFile(file).equals(activeCategory, ignoreCase = true)
                }
            }
        }

        updateList(filtered)
    }

    fun applyCustomList(customList: List<File>, categoryLabel: String = activeCategory) {
        activeCategory = categoryLabel
        searchQuery = ""
        updateList(customList)
    }

    private fun updateList(newList: List<File>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = displayedItems.size
            override fun getNewListSize(): Int = newList.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return displayedItems[oldPos].absolutePath == newList[newPos].absolutePath
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val oldFile = displayedItems[oldPos]
                val newFile = newList[newPos]
                return oldFile.lastModified() == newFile.lastModified() && oldFile.length() == newFile.length()
            }
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)
        displayedItems.clear()
        displayedItems.addAll(newList)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifViewHolder {
        val themedContext = ContextThemeWrapper(parent.context, R.style.Theme_ZenoTap)
        val view = LayoutInflater.from(themedContext).inflate(R.layout.item_gif_cell, parent, false)
        return GifViewHolder(view)
    }

    override fun onBindViewHolder(holder: GifViewHolder, position: Int) {
        holder.bind(displayedItems[position])
    }

    override fun onViewRecycled(holder: GifViewHolder) {
        super.onViewRecycled(holder)
        holder.unbind()
    }

    override fun getItemCount(): Int = displayedItems.size

    inner class GifViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardRoot: View = itemView.findViewById(R.id.card_gif_root)
        private val ivThumb: ImageView = itemView.findViewById(R.id.iv_gif_thumb)
        private val tvName: TextView = itemView.findViewById(R.id.tv_gif_name)
        private val tvSize: TextView = itemView.findViewById(R.id.tv_gif_size)
        private val tvFormat: TextView = itemView.findViewById(R.id.tv_gif_format)
        private val tvPinBadge: TextView? = itemView.findViewById(R.id.tv_pin_badge)
        private val ivFavBadge: ImageView? = itemView.findViewById(R.id.iv_fav_badge)

        fun bind(file: File) {
            tvName.text = DeckStorageManager.getDisplayTitleForFile(file)
            tvSize.text = formatFileSize(file.length())

            val isSticker = file.name.contains("sticker", ignoreCase = true)
            if (isSticker) {
                tvFormat.text = "STICKER"
                tvFormat.setTextColor(0xFF34D399.toInt()) // Emerald
            } else {
                tvFormat.text = "GIF"
                tvFormat.setTextColor(0xFF38BDF8.toInt()) // Cyan
            }

            val isPinned = file.name.startsWith("pinned_", ignoreCase = true)
            tvPinBadge?.visibility = if (isPinned) View.VISIBLE else View.GONE

            // Show favorite star badge if marked as favorite
            val isFav = DeckStorageManager.isFavorite(itemView.context, file)
            ivFavBadge?.visibility = if (isFav) View.VISIBLE else View.GONE

            // Load media with live looping animation & safe fallback (supports GIF & Animated WebP)
            GifThumbnailLoader.loadMedia(file, ivThumb)

            // Tactile touch scale animation & click dispatch
            cardRoot.setOnClickListener {
                cardRoot.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).withEndAction {
                    cardRoot.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }.start()
                onItemClick(file)
            }

            cardRoot.setOnLongClickListener {
                onItemLongClick?.invoke(file)
                true
            }
        }

        fun unbind() {
            try {
                (ivThumb.drawable as? Animatable)?.stop()
                ivThumb.setImageDrawable(null)
            } catch (ignored: Throwable) {}
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1fMB", bytes / (1024f * 1024f))
                bytes >= 1024 -> String.format(Locale.US, "%dKB", bytes / 1024)
                else -> "${bytes}B"
            }
        }
    }
}
