package com.slavabarkov.tidy.adapters

import android.util.Log
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.widget.RecyclerView

class ImageItemKeyProvider(private val adapter: ImageAdapter) : ItemKeyProvider<Long>(SCOPE_MAPPED) {
    override fun getKey(position: Int): Long? {
        // Use the adapter's getItemId which now returns the internalId or NO_ID
        val id = adapter.getItemId(position)
        // Return null if the ID is invalid (RecyclerView.NO_ID)
        return if (id == RecyclerView.NO_ID) null else id
    }

    override fun getPosition(key: Long): Int {
        // Find the position in the dataset based on the internalId
        // Use safe access to dataset and handle case where key isn't found
        val position = adapter.getDataset().indexOfFirst { it.contentId == key }
        // Log if key not found, can happen during state restoration or data changes
        if (position == -1) {
            Log.w("ItemKeyProvider", "Could not find position for key: $key")
        }
        return position
    }
}