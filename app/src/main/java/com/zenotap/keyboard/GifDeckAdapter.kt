package com.zenotap.keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale

/**
 * GifDeckAdapter
 *
 * RecyclerView adapter for binding GIF cards in the ZenoTap keyboard deck.
 */
class GifDeckAdapter(
    private val onItemClick: (File) -> Unit,
    private val onItemLongClick: ((File) -> Unit)? = null
) : RecyclerView.Adapter<GifDeckAdapter.GifViewHolder>() {

    private val items = mutableListOf<File>()

    fun submitList(newItems: List<File>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition].absolutePath == newItems[newItemPosition].absolutePath
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldFile = items[oldItemPosition]
                val newFile = newItems[newItemPosition]
                return oldFile.lastModified() == newFile.lastModified() && oldFile.length() == newFile.length()
            }
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gif_cell, parent, false)
        return GifViewHolder(view)
    }

    override fun onBindViewHolder(holder: GifViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class GifViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumb: ImageView = itemView.findViewById(R.id.iv_gif_thumb)
        private val tvName: TextView = itemView.findViewById(R.id.tv_gif_name)
        private val tvSize: TextView = itemView.findViewById(R.id.tv_gif_size)

        fun bind(file: File) {
            tvName.text = file.nameWithoutExtension.replace('_', ' ')
            tvSize.text = formatFileSize(file.length())

            GifThumbnailLoader.loadThumbnail(file, ivThumb)

            itemView.setOnClickListener {
                onItemClick(file)
            }

            itemView.setOnLongClickListener {
                onItemLongClick?.invoke(file)
                true
            }
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
