package com.slavabarkov.tidy.utils

import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.os.bundleOf
import com.slavabarkov.tidy.data.ImageEmbedding

object GalleryNavigationHelper {
    fun buildGalleryArgs(
        items: List<ImageEmbedding>,
        clickedItem: ImageEmbedding,
        selectedIds: Set<Long> = emptySet(),
        isFromRecycleBin : Boolean
    ): Bundle? {
        val imageUris = items.mapNotNull { emb ->
            when {
                !emb.documentUri.isNullOrBlank() -> emb.documentUri
                emb.mediaStoreId != null -> ContentUris
                    .withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        emb.mediaStoreId
                    ).toString()
                else -> null
            }
        }.toTypedArray()

        val internalIds = items.map { it.contentId }
        val index = items.indexOf(clickedItem)

        return if (index != -1 && imageUris.size == internalIds.size) {
            bundleOf(
                "imageUris" to imageUris,
                "internalIds" to internalIds.toLongArray(),
                "startIndex" to index,
                "selectionModeEnabled" to selectedIds.isNotEmpty(),
                "isFromRecycleBin" to isFromRecycleBin
            )
        } else null
    }
}
