package com.zenotap.keyboard

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.io.File

/**
 * GifDeckAdapter
 *
 * High-performance RecyclerView adapter for binding live animated reaction cards
 * with tactile touch feedback, tag labeling, and real-time category filtering.
 */
class GifDeckAdapter(
    private val onItemClick: (File) -> Unit,
    private val onItemLongClick: ((File) -> Unit)? = null
) : RecyclerView.Adapter<GifDeckAdapter.GifViewHolder>() {

    private val allItems = mutableListOf<File>()
    private val displayedItems = mutableListOf<File>()
    private var activeCategory: String = "All"

    fun setMasterList(files: List<File>) {
        allItems.clear()
        allItems.addAll(files)
        applyFilter()
    }

    fun setCategory(category: String) {
        if (activeCategory == category) return
        activeCategory = category
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (activeCategory == "All") {
            allItems
        } else {
            allItems.filter { file ->
                DeckStorageManager.getCategoryForFile(file).equals(activeCategory, ignoreCase = true)
            }
        }

        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = displayedItems.size
            override fun getNewListSize(): Int = filtered.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return displayedItems[oldPos].absolutePath == filtered[newPos].absolutePath
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val oldFile = displayedItems[oldPos]
                val newFile = filtered[newPos]
                return oldFile.lastModified() == newFile.lastModified() && oldFile.length() == newFile.length()
            }
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)
        displayedItems.clear()
        displayedItems.addAll(filtered)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gif_cell, parent, false)
        return GifViewHolder(view)
    }

    override fun onBindViewHolder(holder: GifViewHolder, position: Int) {
        holder.bind(displayedItems[position])
    }

    override fun getItemCount(): Int = displayedItems.size

    inner class GifViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardRoot: MaterialCardView = itemView.findViewById(R.id.card_gif_root)
        private val ivThumb: ImageView = itemView.findViewById(R.id.iv_gif_thumb)
        private val tvTag: TextView = itemView.findViewById(R.id.tv_gif_tag)

        fun bind(file: File) {
            tvTag.text = DeckStorageManager.getDisplayTitleForFile(file)

            // Load media with live looping animation
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
    }
}
