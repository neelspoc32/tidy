package com.slavabarkov.tidy.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Represents an image embedding stored in the database.
 * It can originate either from the MediaStore or from a user-selected folder via SAF.
 */
@Entity(tableName = "image_embeddings")
@TypeConverters(Converters::class)
data class ImageEmbedding(
    /**
     * Auto-generated primary key for database uniqueness.
     * This is used internally and for stable IDs in the adapter.
     */
    @PrimaryKey(autoGenerate = true)
    val internalId: Long = 0,

    /**
     * The ID from MediaStore (`MediaStore.Images.Media._ID`).
     * This will be NON-NULL only if the image was indexed via MediaStore scan.
     * It will be NULL if the image was indexed from a specific folder via SAF.
     */
    val mediaStoreId: Long?, // Made nullable

    /**
     * The persistent URI string obtained from DocumentFile (`documentFile.uri.toString()`).
     * This will be NON-NULL only if the image was indexed from a specific folder via SAF.
     * It will be NULL if the image was indexed via MediaStore scan.
     */
    val documentUri: String?, // New nullable field

    /**
     * The date the image was last modified (in milliseconds).
     * Obtained from MediaStore.Images.Media.DATE_MODIFIED or DocumentFile.lastModified().
     */
    val date: Long,

    /**
     * The actual embedding vector for the image.
     */
    val embedding: FloatArray,
) {
    @Ignore
    @Transient
    var expiresAt: Long? = null
    // --- Auto-generated equals/hashCode based on all fields ---
    // Note: Default data class equals/hashCode considers all fields, including the embedding array.
    // This might be inefficient if used in Sets/Maps frequently.
    // Consider overriding equals/hashCode to use only IDs if performance becomes an issue.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageEmbedding

        if (internalId != other.internalId) return false
        if (mediaStoreId != other.mediaStoreId) return false
        if (documentUri != other.documentUri) return false
        if (date != other.date) return false
        if (!embedding.contentEquals(other.embedding)) return false // Compare array content

        return true
    }

    override fun hashCode(): Int {
        var result = internalId.hashCode()
        result = 31 * result + (mediaStoreId?.hashCode() ?: 0)
        result = 31 * result + (documentUri?.hashCode() ?: 0)
        result = 31 * result + date.hashCode()
        result = 31 * result + embedding.contentHashCode() // Use content hash code for array
        return result
    }
    // --- End equals/hashCode ---
}