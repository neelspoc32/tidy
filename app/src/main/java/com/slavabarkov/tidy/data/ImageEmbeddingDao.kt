package com.slavabarkov.tidy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object for the ImageEmbedding entity.
 */
@Dao
interface ImageEmbeddingDao {

    /**
     * Inserts an ImageEmbedding into the database.
     * If an embedding with the same internalId already exists, it replaces the old one.
     * NOTE: As internalId is auto-generated, REPLACE based on this PK won't update
     * records based on mediaStoreId or documentUri. Use clearAll() before re-indexing
     * for simplicity or implement manual find-then-update logic if needed.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addImageEmbedding(imageEmbedding: ImageEmbedding)

    /**
     * Retrieves a single ImageEmbedding record based on its internal primary key.
     * @param internalId The auto-generated primary key of the record to retrieve.
     * @return The matching ImageEmbedding object, or null if not found.
     */
    @Query("SELECT * FROM image_embeddings WHERE internalId = :internalId LIMIT 1")
    suspend fun getRecordByInternalId(internalId: Long): ImageEmbedding? // Use new PK and name

    /**
     * Deletes multiple ImageEmbedding records based on their internal primary keys.
     * @param internalIds A list of auto-generated primary keys of the records to delete.
     */
    @Query("DELETE FROM image_embeddings WHERE internalId IN (:internalIds)")
    suspend fun deleteMultipleRecordsByInternalId(internalIds: List<Long>) // Use new PK and name

    /**
     * Deletes all records from the image_embeddings table.
     * Useful before starting a full re-index.
     */
    @Query("DELETE FROM image_embeddings")
    suspend fun clearAll()

    /**
     * Retrieves all ImageEmbedding records from the database.
     * NOTE: This might load a large amount of data into memory if the database is huge.
     * Consider using Paging or limiting the query if performance becomes an issue.
     * @return A list containing all ImageEmbedding objects.
     */
    @Query("SELECT * FROM image_embeddings")
    suspend fun getAllEmbeddings(): List<ImageEmbedding> // Return type is List of new Entity

    @Query("SELECT * FROM image_embeddings WHERE mediaStoreId = :id LIMIT 1")
    suspend fun getByMediaStoreId(id: Long): ImageEmbedding?

    @Query("SELECT * FROM image_embeddings WHERE documentUri = :uri LIMIT 1")
    suspend fun getByDocumentUri(uri: String): ImageEmbedding?

    /**
     * Updates an existing ImageEmbedding record in the database.
     * Matches based on the primary key (internalId) of the provided object.
     */
    @Update
    suspend fun updateImageEmbedding(imageEmbedding: ImageEmbedding) // New method

    @Query("SELECT * FROM image_embeddings WHERE documentUri LIKE :folderUriPrefix || '%'")
    suspend fun getEmbeddingsByFolderPrefix(folderUriPrefix: String): List<ImageEmbedding>

    // --- Keep old methods commented out or remove if no longer needed ---
    // @Query("SELECT * FROM image_embeddings WHERE id = :id LIMIT 1") // Old method based on mediaStoreId PK
    // suspend fun getRecord(id: Long): ImageEmbedding?

    // @Query("DELETE FROM image_embeddings WHERE id IN (:ids)") // Old method based on mediaStoreId PK
    // suspend fun deleteMultipleRecords(ids: List<Long>)

}