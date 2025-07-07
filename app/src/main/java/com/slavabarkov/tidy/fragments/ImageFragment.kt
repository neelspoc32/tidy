/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.slavabarkov.tidy.fragments

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.slavabarkov.tidy.R
import com.slavabarkov.tidy.viewmodels.ORTImageViewModel
import com.slavabarkov.tidy.viewmodels.SearchViewModel
import java.io.File
import java.text.DateFormat
import android.view.animation.OvershootInterpolator
import com.slavabarkov.tidy.viewmodels.RecycleBinViewModel

class ImageFragment : Fragment() {
    // Store the received URI string and internalId
    private var imageUriString: String? = null
    private var internalId: Long? = null // Using the new internal DB ID
    private var isFromRecycleBin: Boolean = false
    // Keep ViewModels
    private val mORTImageViewModel: ORTImageViewModel by activityViewModels()
    private val mSearchViewModel: SearchViewModel by activityViewModels()
    private val mRecycleBinVM: RecycleBinViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_image, container, false)
        // Use requireArguments() which throws an exception if arguments are null.
        // This is safer than this.arguments which can be null.
        val args = requireArguments()


        // Get the internalId (use getLong with a default value just in case, though requireArguments helps)
        // The key "internalId" MUST match the <argument> name in navigation.xml
        internalId = args.getLong("internalId", -1L)

        // Get the URI string (use getString with a default value)
        // The key "imageUriString" MUST match the <argument> name in navigation.xml
        imageUriString = args.getString("imageUriString", "")
        Log.d("ImageFragment", "Received internalId: $internalId, URI string: $imageUriString")
        isFromRecycleBin = args.getBoolean("isFromRecycleBin",false)
        // --- END: Argument Retrieval ---

        // Basic validation
        if (imageUriString == null || internalId == null) {
            Log.e("ImageFragment", "Missing required arguments (internalId or image_uri_string).")
            Toast.makeText(context, "Error loading image details.", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack() // Go back if arguments missing
            return null
        }

        // Parse the received URI string
        val imageUri: Uri = try {
            imageUriString!!.toUri()
        } catch (e: Exception) {
            Log.e("ImageFragment", "Error parsing URI string: $imageUriString", e)
            Toast.makeText(context, "Error loading image.", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return null
        }


        // --- Load Image using Glide (Handles both URI types) ---
        val singleImageView: PhotoView = view.findViewById(R.id.singeImageView)
        Glide.with(view)
            .load(imageUri) // Pass the parsed Uri
            .placeholder(R.drawable.ic_baseline_image_24) // Add placeholder
            .error(R.drawable.ic_baseline_broken_image_24) // Add error placeholder
            .into(singleImageView)
        // --- End Load Image ---


        // --- Metadata Loading (Date & Path) - Adapted Logic ---
        val dateTextView: TextView = view.findViewById(R.id.dateTextView)
        val locationTextView: TextView = view.findViewById(R.id.locationTextView)

        var displayDate: Long? = null
        var displayPath: String? = null
        val contentResolver = requireContext().contentResolver

        try {
            // Attempt to query metadata using ContentResolver (works for both URI types generally)
            val projection = arrayOf(
                MediaStore.MediaColumns.DATE_MODIFIED, // Usually available via ContentResolver query
                MediaStore.MediaColumns.DISPLAY_NAME, // Usually available
                // Only try to get these if potentially a MediaStore URI (optional optimization)
                // Could check uri.authority == "media"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
            )

            contentResolver.query(imageUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dateColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)

                    if (dateColumn != -1) {
                        val dateValue = cursor.getLong(dateColumn)
                        displayDate = if ("media".equals(imageUri.authority, ignoreCase = true)) {
                            dateValue * 1000 // Convert seconds to ms for MediaStore
                        } else {
                            dateValue // Assume other providers use ms
                        }
                    }

                    if (nameColumn != -1) {
                        // Get display name as potential fallback path
                        displayPath = cursor.getString(nameColumn)
                    }
                }
            }
        } catch (e: Exception) {
            // Query might fail, especially for some Document URIs or if columns don't exist
            Log.w("ImageFragment", "ContentResolver query failed for metadata on URI: $imageUri", e)
        }

        // --- Fallback / Augmentation using DocumentFile API if it's a document URI ---
        // Check if query failed to get data or if it looks like a Document URI
        if (displayDate == null || displayPath == null || displayDate!! <= 0) {
            if (DocumentsContract.isDocumentUri(requireContext(), imageUri)) {
                Log.d("ImageFragment", "Using DocumentFile API as fallback/augmentation for metadata.")
                try {
                    val docFile = DocumentFile.fromSingleUri(requireContext(), imageUri)
                    if (docFile != null) {
                        if (displayDate == null || displayDate!! <= 0 ) displayDate = docFile.lastModified() // Already in ms
                        if (displayPath == null) displayPath = docFile.name // Often just the filename
                        // Note: Getting a full "path" from DocumentFile is not straightforward
                        // and usually not recommended. Displaying filename might be sufficient.
                    }
                } catch (e: Exception) {
                    Log.e("ImageFragment", "Error using DocumentFile API for URI: $imageUri", e)
                }
            }
        }
        // --- End Metadata Fallback ---

        // Display metadata (with defaults)
        dateTextView.text = displayDate?.let { DateFormat.getDateInstance().format(it) } ?: "Date unavailable"
        // Use the best path found, or fallback to the URI string itself
        locationTextView.text = displayPath ?: imageUriString
        // --- End Metadata Loading ---


        // --- Button Logic ---
        val buttonImage2Image: Button = view.findViewById(R.id.buttonImage2Image)
        var imageEmbedding = mORTImageViewModel.getAllLoadedEmbeddingsMap()[internalId]
        val openedFromRecycleBin = isFromRecycleBin || (imageEmbedding?.expiresAt != null)
        buttonImage2Image.visibility = if (openedFromRecycleBin) View.GONE else View.VISIBLE
        buttonImage2Image.setOnClickListener {
            Log.d("ImageFragment", "Image2Image button clicked for internalId: $internalId") // Log ID being used
            // Image-to-Image search needs the embedding.
            // We can't easily get it just from internalId/URI here.
            // OPTION 1 (Better): Modify I2I search to accept the image URI directly.
            //                      The ViewModel could then load the bitmap and generate embedding on demand.
            // OPTION 2 (Requires DB access): Use internalId to fetch the ImageEmbedding from DB.
            //                      This adds DB dependency to the Fragment (not ideal) or requires
            //                      passing the embedding through arguments (can be large).
            // OPTION 3 (Simpler if ViewModel holds data): Find embedding in ViewModel list using internalId.
            // Get the current map from the ViewModel
            val currentEmbeddingMap = mORTImageViewModel.getAllLoadedEmbeddingsMap()
            Log.d("ImageFragment", "I2I: Map size: ${currentEmbeddingMap.size}")
            Log.d("ImageFragment", "I2I: Map contains key $internalId: ${currentEmbeddingMap.containsKey(internalId)}")

            val imageEmbeddingObject = mORTImageViewModel.getAllLoadedEmbeddingsMap()[internalId]
            val embedding = imageEmbeddingObject?.embedding // Get the FloatArray from the object
            if (embedding != null) {
                // Optional: Log a small part of the embedding to verify it's not zeros
                Log.d("ImageFragment", "I2I: Embedding sample: [${embedding.take(5).joinToString()}]")
                Log.d("ImageFragment", "I2I: Calling sortByCosineDistance...")
                Log.d("ImageFragment", "Embedding found for internalId: $internalId")
                mSearchViewModel.sortByCosineDistance(
                    embedding,
                    mORTImageViewModel.embeddingsList,
                    mORTImageViewModel.idxList // Pass internal IDs
                )
                Log.d("ImageFragment", "I2I: sortByCosineDistance called.")
                mSearchViewModel.fromImg2ImgFlag = true
                findNavController().popBackStack()
            } else {
                Log.e("ImageFragment", "Could not find embedding for internalId: $internalId to perform I2I search.")
                Toast.makeText(context, "Error finding image data for search.", Toast.LENGTH_SHORT).show()
            }
        }

        // Share button uses the URI directly, which should still work
        val buttonShare: Button = view.findViewById(R.id.buttonShare)
        buttonShare.visibility = if (openedFromRecycleBin) View.GONE else View.VISIBLE
        buttonShare.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, imageUri) // Use the parsed Uri
                type = requireContext().contentResolver.getType(imageUri) ?: "image/*" // Get MIME type
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            try {
                startActivity(shareIntent)
            } catch (e: Exception) {
                Log.e("ImageFragment", "Error starting share intent", e)
                Toast.makeText(context, "Cannot share image.", Toast.LENGTH_SHORT).show()
            }
        }
        // --- End Button Logic ---

        // --- Checkbox selection logic ---
        val selectionMode = arguments?.getBoolean("selectionModeEnabled") ?: false
        val checkBox = view.findViewById<CheckBox>(R.id.fullScreenCheckbox)

        if (selectionMode) {
            checkBox.visibility = View.VISIBLE

            checkBox.apply {
                scaleX = 0.7f
                scaleY = 0.7f
                alpha = 0f
                visibility = View.VISIBLE

                animate()
                    .alpha(1f)
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setInterpolator(OvershootInterpolator(2f)) // springy pop
                    .setDuration(200)
                    .start()
            }

            // Pre-check if this image was already selected
            val ownerVM = if (openedFromRecycleBin) mRecycleBinVM else mSearchViewModel
            if (ownerVM.selectedItemIds.value?.contains(internalId) == true) {
                checkBox.isChecked = true
            }

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                internalId?.let {
                    val updated = (ownerVM.selectedItemIds.value ?: emptySet()).toMutableSet()
                    if (isChecked) updated.add(it) else updated.remove(it)
                    ownerVM.saveSelection(updated)
                }
                }

        }
        return view
    }
    // --- *** NEW Helper Function to Get Path *** ---
    private fun getPathFromUri(contentResolver: ContentResolver, uri: Uri): String? {
        var filePath: String? = null
        // Define columns to query based on Android version
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.DISPLAY_NAME)
        } else {
            arrayOf(MediaStore.Images.Media.DATA)
        }

        try {
            val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)
            cursor?.use { // Use 'use' for automatic closing
                if (it.moveToFirst()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val relativePathColumn = it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                        val displayNameColumn = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)

                        if (relativePathColumn != -1 && displayNameColumn != -1) {
                            val relativePath = it.getString(relativePathColumn)
                            val displayName = it.getString(displayNameColumn)
                            // Combine relative path and display name. You might want to adjust this
                            // based on how you want to display it (e.g., add base storage path if known)
                            // For simplicity, just showing relative path + filename:
                            val sanitizedDisplayName = displayName?.replace("/", "_")?.replace("\\", "_") ?: "unknown_file"
                            filePath = if (relativePath.isNullOrEmpty()) {
                                sanitizedDisplayName
                            }
                            else {
                                File(relativePath, sanitizedDisplayName).path
                            }
                        } else {
                            Log.w("ImageFragment", "RELATIVE_PATH or DISPLAY_NAME column not found on API ${Build.VERSION.SDK_INT}.")
                            // Fallback attempt for Q+ if columns missing (unlikely for standard MediaStore)
                            filePath = getPathWithLegacyColumn(it)
                        }
                    } else {
                        // Legacy approach for older Android versions
                        filePath = getPathWithLegacyColumn(it)
                    }
                }
            } ?: Log.w("ImageFragment", "Path cursor is null for URI: $uri")
        } catch (e: Exception) {
            Log.e("ImageFragment", "Error querying image path for URI: $uri", e)
        }
        return filePath
    }

    // Helper for the deprecated DATA column
    private fun getPathWithLegacyColumn(cursor: Cursor) : String? {
        val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
        return if (dataColumn != -1) {
            cursor.getString(dataColumn)
        } else {
            Log.w("ImageFragment", "DATA column not found.")
            null
        }
    }
}