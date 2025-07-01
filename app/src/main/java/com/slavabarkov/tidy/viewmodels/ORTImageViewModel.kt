// tidy/app/src/main/java/com/slavabarkov/tidy/viewmodels/ORTImageViewModel.kt
package com.slavabarkov.tidy.viewmodels


import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentUris
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.*
import androidx.room.Room
import com.slavabarkov.tidy.DIM_BATCH_SIZE
import com.slavabarkov.tidy.DIM_PIXEL_SIZE
import com.slavabarkov.tidy.IMAGE_SIZE_X
import com.slavabarkov.tidy.IMAGE_SIZE_Y
import com.slavabarkov.tidy.R
import com.slavabarkov.tidy.centerCrop
import com.slavabarkov.tidy.data.ImageEmbedding
import com.slavabarkov.tidy.data.ImageEmbeddingDao
import com.slavabarkov.tidy.data.ImageEmbeddingDatabase
import com.slavabarkov.tidy.data.ImageEmbeddingRepository
import com.slavabarkov.tidy.preProcess
import com.slavabarkov.tidy.utils.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.nio.FloatBuffer
import kotlin.math.sqrt

// Data class for status updates
data class ProcessingStatus(
    val isProcessing: Boolean = false,
    val messageResId: Int = R.string.index_status_idle,
    val progress: Int = 0,
    val maxProgress: Int = 100
)

// Define an enum to represent the indexing scope status
enum class IndexingScopeStatus {
    NONE, // No indexing has been done, or data has been cleared
    FULL_DEVICE_INDEXED, // Entire device media store has been indexed
    FOLDER_INDEXED // A specific folder has been indexed
}

// ViewModel needs Application context now
@SuppressLint("StaticFieldLeak")
class ORTImageViewModel(application: Application) : AndroidViewModel(application) {

    private val _mProcessingStatus = MutableLiveData(
        ProcessingStatus(isProcessing = true, messageResId = R.string.index_status_initializing)
    )
    val mProcessingStatus: LiveData<ProcessingStatus> = _mProcessingStatus
    // LiveData to indicate if the image database is ready (loaded from disk)
    private val _isDataReady = MutableLiveData<Boolean>()
    val isDataReady: LiveData<Boolean> get() = _isDataReady

    private var repository: ImageEmbeddingRepository
    var embeddingsList: List<FloatArray> = listOf()
    var idxList: List<Long> = listOf()
    private var fullEmbeddingData: List<ImageEmbedding> = listOf() // Load this in loadEmbeddingsFromDb
    private var embeddingMap: Map<Long, ImageEmbedding> = mapOf()
    private val embeddingDim = 512 // Assuming CLIP ViT-B/32
    // LiveData to hold the current indexing scope status
    private val _indexingScopeStatus = MutableLiveData<IndexingScopeStatus>()
    val indexingScopeStatus: LiveData<IndexingScopeStatus> get() = _indexingScopeStatus

    private val context = application.applicationContext
    // --- Database Access ---
    private val db = Room.databaseBuilder(
        application.applicationContext,
        ImageEmbeddingDatabase::class.java, "image_embeddings_db"
    ).fallbackToDestructiveMigration().build()

    private val imageEmbeddingDao: ImageEmbeddingDao = db.imageEmbeddingDao()
    // ---------------------


    // Load ONNX model (existing logic - needs try/catch)
    private var ortSession: OrtSession? = null
    private var ortEnv: OrtEnvironment? = null

    init {
        _mProcessingStatus.postValue(
            ProcessingStatus(
                isProcessing = true,
                messageResId = R.string.index_status_initializing
            )
        )

        val imageEmbeddingDao = ImageEmbeddingDatabase.getDatabase(application).imageEmbeddingDao()
        repository = ImageEmbeddingRepository(imageEmbeddingDao)
        viewModelScope.launch(Dispatchers.IO) {
            var modelLoadedSuccessfully = false
            var finalStatusMsg = R.string.index_status_idle
            var finalIsProcessing = true
            try {
                ortEnv = OrtEnvironment.getEnvironment()
                val sessionOptions = OrtSession.SessionOptions()
                sessionOptions.registerCustomOpLibrary(OrtxPackage.getLibraryPath())

                val modelBytes = application.resources.openRawResource(R.raw.visual_quant).readBytes()
                ortSession = ortEnv?.createSession(modelBytes, sessionOptions)

                if (ortSession != null) {
                    Log.i("ORTImageViewModel", "ONNX Model loaded successfully.")
                    modelLoadedSuccessfully = true
                    _mProcessingStatus.postValue(
                        ProcessingStatus(
                            isProcessing = true,
                            messageResId = R.string.index_status_ready
                        )
                    )
                } else {
                    Log.e("ORTImageViewModel", "ORT Session creation returned null.")
                    finalStatusMsg = R.string.error_loading_model
                    finalIsProcessing = false
                    _mProcessingStatus.postValue(
                        ProcessingStatus(
                            isProcessing = false,
                            messageResId = R.string.error_loading_model
                        )
                    )
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("ORTImageViewModel", "Error loading ONNX model", e)
                finalStatusMsg = R.string.error_loading_model
                finalIsProcessing = false
                _mProcessingStatus.postValue(
                    ProcessingStatus(
                        isProcessing = finalIsProcessing,
                        messageResId = finalStatusMsg
                    )
                )
                return@launch
            }

            Log.d("ORTImageViewModel", "Attempting to load embeddings from DB after initialization.")
            _mProcessingStatus.postValue(
                ProcessingStatus(
                    isProcessing = finalIsProcessing,
                    messageResId = R.string.index_status_loading_db
                )
            )

            loadEmbeddingsFromDb()

            finalStatusMsg = if (embeddingsList.isNotEmpty()) R.string.index_status_ready else R.string.index_status_idle
            finalIsProcessing = false
            _mProcessingStatus.postValue(
                ProcessingStatus(
                    isProcessing = finalIsProcessing,
                    messageResId = finalStatusMsg
                )
            )
            Log.d("ORTImageViewModel", "Finished loading embeddings from DB. Status set to: $finalStatusMsg"
            )
        }

        // Load persisted indexing scope status on ViewModel initialization
        _indexingScopeStatus.value = PreferencesHelper.getIndexingScopeStatus(context)

        Log.d("ORTImageViewModel", "Initial Indexing Scope Status loaded: ${_indexingScopeStatus.value}")
//        viewModelScope.launch(Dispatchers.IO) {
//        // Load embeddings and indices from the database
//        loadEmbeddingsFromDb()
//        }
    }


    // *** NEW: Function to clear embeddings ***
    fun clearAllEmbeddings(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("ORTImageViewModel", "Clearing all embeddings...")
                // Clear internal lists immediately for responsiveness
                embeddingsList = emptyList()
                idxList = emptyList()
                fullEmbeddingData = emptyList()
                embeddingMap = emptyMap()
                // Clear database
                imageEmbeddingDao.clearAll()
                // After all deletions, if data list became empty, reset status
//                if (embeddingsList.isEmpty()) {
//                withContext(Dispatchers.Main) {
//                   _indexingScopeStatus.value = IndexingScopeStatus.NONE
//                   PreferencesHelper.saveIndexingScopeStatus(context, IndexingScopeStatus.NONE)
//                    PreferencesHelper.saveSelectedFolderUri(context, null) // Clear folder URI too
//
//                Log.d("ORTImageViewModel", "All images deleted, status reset to NONE.")
//                }
                Log.d("ORTImageViewModel", "Embeddings cleared.")
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                Log.e("ORTImageViewModel", "Error clearing embeddings", e)
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }
    // *****************************************

    // *** REVISED: Main function to trigger indexing ***
    fun startIndexing() {

        if (_mProcessingStatus.value?.isProcessing == true) {
            Log.w("ORTImageViewModel", "Indexing already in progress.")
            return
        }
        if (ortSession == null) {
            Log.e("ORTImageViewModel", "ONNX session not ready, cannot start indexing.")
            _mProcessingStatus.postValue(ProcessingStatus(messageResId = R.string.error_model_not_ready))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _mProcessingStatus.postValue(
                ProcessingStatus(
                    isProcessing = true,
                    messageResId = R.string.index_status_starting
                )
            )
            val selectedFolderUri = PreferencesHelper.getSelectedFolderUri(getApplication())
            var success = false // Track if indexing completed without major error

            try {
                if (selectedFolderUri == null) {
                    Log.i(
                        "ORTImageViewModel",
                        "No folder selected, indexing all MediaStore images."
                    )
                    indexMediaStoreImages() // This function should handle its own final success/error status
                    // Set status to FULL_DEVICE_INDEXED upon successful full indexing
                    withContext(Dispatchers.Main) {
                        _indexingScopeStatus.value = IndexingScopeStatus.FULL_DEVICE_INDEXED
                        PreferencesHelper.saveIndexingScopeStatus(context, IndexingScopeStatus.FULL_DEVICE_INDEXED)
                        Log.d("ORTImageViewModel", "Indexing Scope Status updated to FULL_DEVICE_INDEXED")
                    }
                } else {
                    Log.i("ORTImageViewModel", "Folder selected, indexing: $selectedFolderUri")
                    indexSpecificFolder(selectedFolderUri) // This function should handle its own final success/error status
                    // Set status to FOLDER_INDEXED upon successful folder indexing
                    withContext(Dispatchers.Main) {
                        _indexingScopeStatus.value = IndexingScopeStatus.FOLDER_INDEXED
                        PreferencesHelper.saveSelectedFolderUri(context, selectedFolderUri) // Also save folder URI
                        PreferencesHelper.saveIndexingScopeStatus(context, IndexingScopeStatus.FOLDER_INDEXED)
                        Log.d("ORTImageViewModel", "Indexing Scope Status updated to FOLDER_INDEXED for URI: $selectedFolderUri")
                    }
                }
                success = true // Assume success if no exception bubbles up here

            } catch (e: Exception) {
                Log.e("ORTImageViewModel", "Error during indexing process", e)
                _mProcessingStatus.postValue(
                    ProcessingStatus(
                        isProcessing = false,
                        messageResId = R.string.index_status_error
                    )
                )
                success = false
            } finally {
                // Load results regardless of success/failure? Or only on success?
                // Let's load regardless to potentially show partial results
                Log.d(
                    "ORTImageViewModel",
                    "Indexing finished (Success: $success). Loading results from DB."
                )
                loadEmbeddingsFromDb()
                // Final status update moved inside specific indexing functions
                // Only post idle/ready status if not already handled by specific functions
                if (_mProcessingStatus.value?.isProcessing == true) { // Check if status wasn't finalized by specific indexer
                    val finalMsg =
                        if (idxList.isNotEmpty()) R.string.index_status_ready else R.string.index_status_idle
                    _mProcessingStatus.postValue(
                        ProcessingStatus(
                            isProcessing = false,
                            messageResId = finalMsg
                        )
                    )
                }
            }
        }
    }
    // *************************************************

    // --- Existing MediaStore Indexing Logic (Refactored) ---
    @SuppressLint("Range") // Suppress Range warning as we check indices
    private suspend fun indexMediaStoreImages() {
        val context = getApplication<Application>().applicationContext
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        var cursor: Cursor? = null
        var success = false
        var totalImages = 0
        var processedCount = 0

        try {
            cursor = context.contentResolver.query(collection, projection, null, null, null)

            cursor?.use { // Use 'use' for automatic closing
                val idColumn = it.getColumnIndex(MediaStore.Images.Media._ID)
                val dateColumn = it.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)

                totalImages = it.count
                _mProcessingStatus.postValue(
                    ProcessingStatus(
                        isProcessing = true,
                        messageResId = R.string.index_status_processing,
                        progress = 0,
                        maxProgress = totalImages
                    )
                )

                while (it.moveToNext()) {
                    var itemProcessedSuccessfully = false // Track success for this specific item
                    var currentId : Long? = null // Store ID for logging in finally
                    try {
                        if (idColumn == -1 || dateColumn == -1) {
                            Log.e("ORTImageViewModel", "Required MediaStore columns not found.")
                            continue // Skip this item
                        }
                        val id = it.getLong(idColumn)
                        currentId = id // Store for finally block
                        val date = it.getLong(dateColumn)
                        val contentUri: Uri = ContentUris.withAppendedId(collection, id)

                        processAndSaveEmbedding(
                            imageUri = contentUri,
                            mediaStoreImageId = id,
                            documentUriString = null,
                            date = date * 1000
                        )
                        itemProcessedSuccessfully = true // Mark as processed if no exception

                    } catch (itemEx: Exception) {
                        Log.e(
                            "ORTImageViewModel",
                            "Error processing MediaStore item URI: ${
                                ContentUris.withAppendedId(
                                    collection,
                                    it.getLong(idColumn)
                                )
                            }",
                            itemEx
                        )
                        // Keep going with the next item
                    } finally {
                        // Increment count regardless of item success/failure
                        processedCount++
                        // Update progress after every item for smoother UI
                        _mProcessingStatus.postValue(
                            ProcessingStatus(
                                isProcessing = true,
                                messageResId = R.string.index_status_processing,
                                progress = processedCount,
                                maxProgress = totalImages
                            )
                        )
                    }
                }
                success = true // Mark overall success if loop completes
            } ?: Log.w("ORTImageViewModel", "MediaStore cursor is null.")

        } catch (e: Exception) {
            Log.e("ORTImageViewModel", "Error querying MediaStore", e)
            success = false
        } finally {
            Log.d("ORTImageViewModel", "MediaStore indexing loop finished. Success: $success. Processed: $processedCount/$totalImages")
            // Load embeddings from DB AFTER the loop finishes
            loadEmbeddingsFromDb() // Wait for DB load to complete
            Log.d("ORTImageViewModel", "Embeddings loaded from DB after MediaStore indexing.")
            // Final status update for MediaStore indexing
            val finalMessage =
                if (success) R.string.index_status_complete else R.string.index_status_error
            _mProcessingStatus.postValue(
                ProcessingStatus(
                    isProcessing = false,
                    messageResId = finalMessage,
                    progress = processedCount,
                    maxProgress = totalImages
                )
            )
        }
    }
    // --- End MediaStore Indexing ---

    // *** NEW: Logic for Indexing a Specific Folder using DocumentFile ***
    private suspend fun indexSpecificFolder(folderUri: Uri) {
        val context = getApplication<Application>().applicationContext
        var rootDocFile: DocumentFile? = null
        var success = false
        var totalImages = 0
        var processedCount = 0

        try {
            rootDocFile = DocumentFile.fromTreeUri(context, folderUri)

            if (rootDocFile == null || !rootDocFile.isDirectory) {
                Log.e("ORTImageViewModel", "Provided URI is not a valid directory: $folderUri")
                _mProcessingStatus.postValue(
                    ProcessingStatus(
                        isProcessing = false,
                        messageResId = R.string.error_invalid_folder
                    )
                )
                return // Exit early if folder is invalid
            }

            val imageFiles = mutableListOf<DocumentFile>()

            // Recursive function to find all image files
            suspend fun findImageFiles(directory: DocumentFile) {
                // Check if directory can be listed - handle potential errors
                try {
                    directory.listFiles().forEach { file ->
                        try { // Add inner try-catch for individual file access
                            if (file.isDirectory) {
                                findImageFiles(file) // Recurse into subdirectories
                            } else if (file.isFile && file.type?.startsWith("image/") == true) {
                                imageFiles.add(file)
                            }
                        } catch (fileEx: Exception) {
                            Log.e(
                                "ORTImageViewModel",
                                "Error accessing file/directory: ${file.uri}",
                                fileEx
                            )
                        }
                    }
                } catch (listEx: Exception) {
                    Log.e(
                        "ORTImageViewModel",
                        "Error listing files in directory: ${directory.uri}",
                        listEx
                    )
                    // Decide how to handle: stop indexing? continue? For now, log and continue.
                }
            }

            _mProcessingStatus.postValue(
                ProcessingStatus(
                    isProcessing = true,
                    messageResId = R.string.index_status_finding_files
                )
            )
            findImageFiles(rootDocFile) // Find all images first to get total count

            totalImages = imageFiles.size
            _mProcessingStatus.postValue(
                ProcessingStatus(
                    isProcessing = true,
                    messageResId = R.string.index_status_processing,
                    progress = 0,
                    maxProgress = totalImages
                )
            )
            Log.i("ORTImageViewModel", "Found $totalImages images in selected folder.")

            imageFiles.forEach { docFile ->
                var itemProcessedSuccessfully = false // Track success for this specific item
                try {
                    val date = docFile.lastModified() // Already in milliseconds
                    val persistentUriString = docFile.uri?.toString() // Get the URI string

                    if (docFile.uri != null && persistentUriString != null) {
                        processAndSaveEmbedding(
                            imageUri = docFile.uri,
                            mediaStoreImageId = null,
                            documentUriString = persistentUriString,
                            date = date
                        )
                        itemProcessedSuccessfully = true // Mark as processed if no exception
                    } else {
                        Log.w(
                            "ORTImageViewModel",
                            "DocumentFile has null URI or failed toString: ${docFile.name}"
                        )
                    }
                } catch (itemEx: Exception) {
                    Log.e(
                        "ORTImageViewModel",
                        "Error processing DocumentFile: ${docFile.name}",
                        itemEx
                    )
                    // Keep going with the next file
                } finally {
                    // --- START: Modified Progress Update ---
                    // Increment count regardless of item success/failure
                    processedCount++
                    // Update progress after EVERY item for smoother UI
                    _mProcessingStatus.postValue(
                        ProcessingStatus(
                            isProcessing = true,
                            messageResId = R.string.index_status_processing,
                            progress = processedCount,
                            maxProgress = totalImages
                        )
                    )
                    // --- END: Modified Progress Update ---
                }
            }
            success = true // Mark overall success if loop completes without major error

        } catch (e: Exception) {
            Log.e("ORTImageViewModel", "Error during folder indexing setup or file finding", e)
            success = false
        } finally {
            Log.d("ORTImageViewModel", "Folder indexing loop finished. Success: $success. Processed: $processedCount/$totalImages")
            // Load embeddings from DB AFTER the loop finishes
            loadEmbeddingsFromDb() // Wait for DB load to complete
            Log.d("ORTImageViewModel", "Embeddings loaded from DB after folder indexing.")
            // --- START: Added Final Update ---
            // Final status update specifically for folder indexing
            val finalMessage =
                if (success) R.string.index_status_complete else R.string.index_status_error
            // Ensure progress reflects the final count attempted, even if errors occurred
            _mProcessingStatus.postValue(
                ProcessingStatus(
                    isProcessing = false,
                    messageResId = finalMessage,
                    progress = processedCount,
                    maxProgress = totalImages
                )
            )
            // --- END: Added Final Update ---
        }
    }
    // **********************************************************************


    // --- Helper to process a single image URI (REVISED) ---
    private suspend fun processAndSaveEmbedding(
        imageUri: Uri,
        mediaStoreImageId: Long?, // Nullable MediaStore ID
        documentUriString: String?, // Nullable Document URI String
        date: Long,
    ) {
        val context = getApplication<Application>().applicationContext
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Log.w("ORTImageViewModel", "Could not open InputStream for URI: $imageUri")
                return // Return early if stream is null
            }
            // Use BitmapFactory options to avoid loading huge bitmaps if possible
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888 // Or RGB_565 for less memory
                // Consider adding inSampleSize for large images if memory is an issue
                // inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)
            }

            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            if (bitmap == null) {
                Log.w("ORTImageViewModel", "Could not decode Bitmap for URI: $imageUri")
                // No need to close stream here, finally block handles it
                return // Return early if bitmap is null
            }

            // --- Use functions from ImageUtil.kt ---
            val croppedBitmap = centerCrop(bitmap, IMAGE_SIZE_X)
            val imageTensorBuffer = preProcess(croppedBitmap)
            // --- End ImageUtil.kt function usage ---

            val embedding = getEmbedding(imageTensorBuffer)

            // Check if embedding is valid before saving (e.g., not all zeros if that indicates error)
            if (embedding.any { it != 0f }) { // Basic check if embedding is not all zeros
                val imageEmbedding = ImageEmbedding(
                    mediaStoreId = mediaStoreImageId,
                    documentUri = documentUriString,
                    date = date,
                    embedding = embedding
                )
                imageEmbeddingDao.addImageEmbedding(imageEmbedding)
            } else {
                Log.w(
                    "ORTImageViewModel",
                    "Generated embedding is potentially invalid (e.g., all zeros) for URI: $imageUri. Skipping save."
                )
            }


            // Recycle bitmaps
            bitmap.recycle()
            croppedBitmap.recycle()

        } catch (e: Exception) {
            // Log specific errors for bitmap decoding, processing, or saving
            Log.e("ORTImageViewModel", "Failed to process or save embedding for URI: $imageUri", e)
            // Re-throw if you want the outer loop to catch it, or handle here
            // throw e // Optional: re-throw to indicate failure to the caller loop
        } finally {
            // Ensure the InputStream is always closed
            try {
                withContext(Dispatchers.IO) {
                    inputStream?.close()
                }
            } catch (ioe: IOException) {
                Log.e("ORTImageViewModel", "Error closing input stream for $imageUri", ioe)
            }
        }
    }
// --- End Helper ---


    // --- Existing function to get embedding (REVISED again for OnnxTensor) ---
    private fun getEmbedding(imageTensorBuffer: FloatBuffer): FloatArray {
        val currentOrtEnv = ortEnv ?: run {
            Log.e("ORTImageViewModel", "ORT environment is null, cannot create tensor.")
            return FloatArray(embeddingDim) { 0f } // Return zero array
        }
        val currentOrtSession = ortSession ?: run {
            Log.e("ORTImageViewModel", "ORT session is null, cannot run inference.")
            return FloatArray(embeddingDim) { 0f } // Return zero array
        }

        var onnxTensor: OnnxTensor? = null
        var results: OrtSession.Result? = null
        val errorResult = FloatArray(embeddingDim) { 0f } // Predefined error result

        try {
            val tensorShape = longArrayOf(
                DIM_BATCH_SIZE.toLong(),
                DIM_PIXEL_SIZE.toLong(),
                IMAGE_SIZE_X.toLong(),
                IMAGE_SIZE_Y.toLong()
            )
            onnxTensor = OnnxTensor.createTensor(currentOrtEnv, imageTensorBuffer, tensorShape)

            val inputName = currentOrtSession.inputNames?.iterator()?.next() ?: run {
                Log.e("ORTImageViewModel", "Could not get input name from session.")
                return errorResult
            }
            val input = mapOf(inputName to onnxTensor)

            results = currentOrtSession.run(input)

            val outputResultValue = results?.get(0)?.value ?: run {
                Log.e("ORTImageViewModel", "Inference result output or value is null.")
                return errorResult
            }

            val embeddingArray = outputResultValue as? FloatArray

            if (embeddingArray == null) {
                Log.e(
                    "ORTImageViewModel",
                    "Could not cast result value directly to FloatArray. Actual type: ${outputResultValue::class.java.name}"
                )
                if (outputResultValue is Array<*> && outputResultValue.isNotEmpty() && outputResultValue[0] is FloatArray) {
                    Log.w(
                        "ORTImageViewModel",
                        "Output seems to be Array<FloatArray>, attempting to extract inner array."
                    )
                    try {
                        val nestedArray = outputResultValue[0] as FloatArray
                        if (nestedArray.size == embeddingDim) return nestedArray
                        else Log.e("ORTImageViewModel", "Nested array size mismatch.")
                    } catch (e: Exception) {
                        Log.e("ORTImageViewModel", "Error extracting nested FloatArray", e)
                    }
                }
                return errorResult // Return zero array on failure
            }

            if (embeddingArray.size != embeddingDim) {
                Log.e(
                    "ORTImageViewModel",
                    "Output embedding size (${embeddingArray.size}) does not match expected dimension ($embeddingDim)."
                )
                return errorResult
            }

            // Log.d("ORTImageViewModel", "Successfully extracted embedding FloatArray.") // Reduce log frequency
            return embeddingArray

        } catch (e: Exception) {
            Log.e("ORTImageViewModel", "Error during getEmbedding execution", e)
            return errorResult
        } finally {
            // Close resources safely
            try {
                onnxTensor?.close()
            } catch (e: Exception) {
                Log.e("ORTImageViewModel", "Error closing OnnxTensor", e)
            }
            try {
                results?.close()
            } catch (e: Exception) {
                Log.e("ORTImageViewModel", "Error closing OrtSession.Result", e)
            }
        }
    }
// --- End getEmbedding ---


    // --- Load Embeddings from DB ---
    suspend fun loadEmbeddingsFromDb() {
        withContext(Dispatchers.IO) { // Ensure DB access is on IO thread
            try {
                Log.d("ORTImageViewModel", "Loading embeddings from DB...")
                val allEmbeddingsData: List<ImageEmbedding> = imageEmbeddingDao.getAllEmbeddings()

                // Post updates to LiveData or StateFlow from Main thread if needed for UI observers
                // For internal lists, update directly here on IO thread is fine
                embeddingsList = allEmbeddingsData.map { it.embedding }
                idxList = allEmbeddingsData.map { it.internalId }
                fullEmbeddingData = allEmbeddingsData
                embeddingMap = allEmbeddingsData.associateBy { it.internalId }
                //_isDataReady.postValue(true)


                withContext(Dispatchers.Main) {
                    _isDataReady.value = true
                    Log.d("ORTImageViewModel", "Embeddings loaded from DB. Count: ${embeddingsList.size}")
                    // After loading, update indexing status if it was NONE but we found data
                    if (_indexingScopeStatus.value == IndexingScopeStatus.NONE && embeddingsList.isNotEmpty()) {
                        // This case might happen if preferences were cleared but DB wasn't, or initial app install
                        // We can try to infer based on selected folder URI
                        val selectedFolderUri = PreferencesHelper.getSelectedFolderUri(context)
                        if (selectedFolderUri != null) {
                            _indexingScopeStatus.value = IndexingScopeStatus.FOLDER_INDEXED
                            Log.d("ORTImageViewModel", "Inferred status: FOLDER_INDEXED (data found and folder URI exists)")
                        } else {
                            // If no folder URI, assume full scan was done if data exists
                            _indexingScopeStatus.value = IndexingScopeStatus.FULL_DEVICE_INDEXED
                            Log.d("ORTImageViewModel", "Inferred status: FULL_DEVICE_INDEXED (data found and no folder URI)")
                        }
                        // Persist the inferred status
                        PreferencesHelper.saveIndexingScopeStatus(context, _indexingScopeStatus.value!!)
                    } else if (_indexingScopeStatus.value != IndexingScopeStatus.NONE && embeddingsList.isEmpty()){
                        // If status says something but DB is empty, reset status
                        _indexingScopeStatus.value = IndexingScopeStatus.NONE
                        PreferencesHelper.saveIndexingScopeStatus(context, IndexingScopeStatus.NONE)
                        Log.d("ORTImageViewModel", "Reset status to NONE: DB empty but preferences indicated otherwise.")
                    }
                }

                Log.d(
                    "ORTImageViewModel",
                    "Loaded ${embeddingsList.size} embeddings with internal IDs."
                )
            } catch (e: Exception) {
                Log.e("ORTImageViewModel", "Error loading embeddings from DB", e)
                // Reset lists on error
                embeddingsList = emptyList()
                idxList = emptyList()
                fullEmbeddingData = emptyList()
                embeddingMap = emptyMap()
                withContext(Dispatchers.Main) {
                    _isDataReady.value = false
                }
            }
        }
    }
    // --- End Load Embeddings ---


    override fun onCleared() {
        super.onCleared()
        // Release ORT resources
        try {
            ortSession?.close()
            ortEnv?.close()
            Log.i("ORTImageViewModel", "ORT resources released.")
        } catch (e: Exception) {
            Log.e("ORTImageViewModel", "Error closing ORT resources", e)
        }
    }

    // --- Removed removeItemsFromIndex and handleSuccessfulDeletions ---
    // Deletion logic is now consolidated in deleteEmbeddingsByInternalId

    fun getAllLoadedEmbeddingsMap(): Map<Long, ImageEmbedding> {
        return embeddingMap
    }

    suspend fun deleteEmbeddingsByInternalId(internalIds: List<Long>) {
        if (internalIds.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                Log.d("ORTImageViewModel", "Deleting ${internalIds.size} embeddings from DB...")
                imageEmbeddingDao.deleteMultipleRecordsByInternalId(internalIds)
                Log.d("ORTImageViewModel", "DB deletion successful.")

                // Update internal lists and map (important!)
                fullEmbeddingData = fullEmbeddingData.filter { it.internalId !in internalIds }
                embeddingMap = fullEmbeddingData.associateBy { it.internalId }
                idxList = fullEmbeddingData.map { it.internalId }
                embeddingsList = fullEmbeddingData.map { it.embedding }
                Log.d("ORTImageViewModel", "Internal lists updated. New count: ${idxList.size}")

            } catch (e: Exception) {
                Log.e("ORTImageViewModel", "Error deleting embeddings from DB", e)
                // Optionally re-load from DB on error? Or just log.
            }
        }
    }

    suspend fun embeddingMoved(internalId: Long, newTimestamp: Long) {
        Log.d(
            "ORTImageViewModel",
            "Processing moved embedding as removal. Internal ID: $internalId"
        )
        // Treat move as a deletion of the old record
        deleteEmbeddingsByInternalId(listOf(internalId))
    }

    fun getImageEmbedding(bitmap: Bitmap): FloatArray {
        val inputTensor = preprocessBitmap(bitmap) // resize, normalize, etc.
        val outputBuffer = Array(1) { FloatArray(512) }

        val currentOrtSession = ortSession ?: return outputBuffer[0]
        val currentOrtEnv = ortEnv ?: return outputBuffer[0]

        val inputName = currentOrtSession.inputNames?.iterator()?.next() ?: run {
            Log.e("ORTImageViewModel", "Could not resolve input name from session.")
            return outputBuffer[0]
        }


        ortSession?.run(
            mapOf(inputName to OnnxTensor.createTensor(currentOrtEnv, inputTensor))
        ).use { result ->
            @Suppress("UNCHECKED_CAST")
            val rawOutput = result?.get(0)?.value as Array<FloatArray>
            outputBuffer[0] = normalizeL2(rawOutput[0])
        }
        return outputBuffer[0]
    }

    // Helper: Normalize to unit length
    private fun normalizeL2(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.map { it * it }.sum())
        return vector.map { it / norm }.toFloatArray()
    }

    // Helper: Preprocess bitmap to tensor
    private fun preprocessBitmap(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val inputSize = 224
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val result = Array(1) { Array(3) { Array(inputSize) { FloatArray(inputSize) } } }

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val px = scaled.getPixel(x, y)
                result[0][0][y][x] = ((px shr 16 and 0xFF) / 255f - 0.481f) / 0.268f
                result[0][1][y][x] = ((px shr 8 and 0xFF) / 255f - 0.457f) / 0.261f
                result[0][2][y][x] = ((px and 0xFF) / 255f - 0.408f) / 0.225f
            }
        }
        return result
    }

}