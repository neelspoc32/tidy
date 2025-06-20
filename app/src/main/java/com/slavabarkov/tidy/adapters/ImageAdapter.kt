/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.slavabarkov.tidy.adapters

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.navigation.findNavController
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.slavabarkov.tidy.R
import com.slavabarkov.tidy.data.ImageEmbedding


// Changed constructor to accept List<ImageEmbedding>
class ImageAdapter(private val context: Context, initialDataset: List<ImageEmbedding>,private val onItemClick: ((ImageEmbedding) -> Unit)? = null) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {
    private var dataset: List<ImageEmbedding> = initialDataset

    lateinit var selectionTracker: SelectionTracker<Long>

    init {
        setHasStableIds(true) // Enable stable IDs
    }

    fun updateData(newDataset:  List<ImageEmbedding>) {
        dataset = newDataset
        Log.d("ImageAdapter", "updateData called. New size: ${newDataset.size}. Selection NOT cleared here.") // Add log
       //selectionTracker.clearSelection() // Reset selection state
        notifyDataSetChanged()
    }

    fun getDataset(): List<ImageEmbedding> = dataset // Add this to access dataset

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.item_image)
        val checkBox: CheckBox = view.findViewById(R.id.imageCheckbox)
        val enlargeButton: ImageButton = view.findViewById(R.id.enlarge_button)
        val textDaysLeft: TextView = view.findViewById(R.id.textDaysLeft)
    }

override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val adapterLayout =
            LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
        return ImageViewHolder(adapterLayout)
    }

    override fun getItemCount(): Int = dataset.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        // Use getOrNull for safety in case dataset changes during binding
        val itemEmbedding = dataset.getOrNull(position)
        val item = dataset[position]
        // val imageUri = Uri.withAppendedPath(uri, item.toString())
        val checkBox = holder.checkBox

        if (itemEmbedding == null) {
            Log.e("ImageAdapter", "Binding failed: itemEmbedding is null for position $position")
            // Clear view or show placeholder
            holder.imageView.setImageResource(R.drawable.ic_baseline_broken_image_24)
            holder.checkBox.visibility = View.GONE
            holder.itemView.setOnClickListener(null) // Remove listeners if item is invalid
            holder.imageView.setOnLongClickListener(null)
            holder.enlargeButton.setOnClickListener(null)
            holder.enlargeButton.setOnTouchListener(null)
            return
        }

        // Use the stable internalId for selection tracking
        val itemInternalId = itemEmbedding.internalId
        // --- Determine the correct URI to load ---
        val imageUriToLoad: Uri? = when {
            !itemEmbedding.documentUri.isNullOrBlank() -> {
                try {
                    itemEmbedding.documentUri.toUri() // Parse the stored String URI
                } catch (e: Exception) {
                    Log.e(
                        "ImageAdapter",
                        "Error parsing document URI: ${itemEmbedding.documentUri} for internalId $itemInternalId",
                        e
                    )
                    null
                }
            }

            itemEmbedding.mediaStoreId != null -> {
                try {
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        itemEmbedding.mediaStoreId
                    )
                } catch (e: Exception) {
                    Log.e(
                        "ImageAdapter",
                        "Error building MediaStore URI for mediaStoreId: ${itemEmbedding.mediaStoreId} for internalId $itemInternalId",
                        e
                    )
                    null
                }
            }

            else -> {
                Log.e(
                    "ImageAdapter",
                    "ImageEmbedding has neither documentUri nor mediaStoreId for internalId: $itemInternalId"
                )
                null
            }
        }
        // --- End URI determination ---


        // Load image using the determined URI with Glide
        if (imageUriToLoad != null) {
            Log.d("AdapterDebug", "Loading URI for ID $itemInternalId: $imageUriToLoad")
            Glide.with(context)
                .load(imageUriToLoad)
                .placeholder(R.drawable.ic_baseline_image_24)
                .error(R.drawable.ic_baseline_broken_image_24)
                .thumbnail()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.imageView)
        } else {
            Log.e("AdapterDebug", "URI is NULL for InternalID: $itemInternalId")
            holder.imageView.setImageResource(R.drawable.ic_baseline_broken_image_24)
        }

        // Selection State Handling
        // --- Selection State Handling (uses internalId now) ---
        val isSelected = selectionTracker.isSelected(itemInternalId)
        holder.itemView.isActivated = isSelected
        holder.checkBox.isChecked = isSelected

        if (itemEmbedding.expiresAt != null) {
            val daysLeft = ((itemEmbedding.expiresAt!! - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
            holder.textDaysLeft.apply {
                text = "Expires in $daysLeft day${if (daysLeft != 1L) "s" else ""}"
                setBackgroundColor(
                    if (daysLeft <= 3) Color.parseColor("#D32F2F") // red
                    else Color.parseColor("#99000000")
                )
                visibility = View.VISIBLE
            }
        } else {
            holder.textDaysLeft.visibility = View.GONE
        }

        // Define the navigation logic (can use explicit label for early return)
        // Define the navigation logic with detailed logs
        // Define the navigation logic lambda (adapted from your original)
        // Moved outside the listener setup for clarity, but still defined within onBindViewHolder scope
        val performNavigation: (Uri?, Long) -> Unit = navLambda@{ uriForNav, internalIdForNav ->
            Log.d("ClickListenerDebug", "==> performNavigation called for internalId: $internalIdForNav")

            val uriString = uriForNav?.toString()
            if (uriString == null) {
                Log.e("ClickListenerDebug", "performNavigation: Image URI is null. Exiting.")
                Toast.makeText(context, "Error: Image data missing.", Toast.LENGTH_SHORT).show()
                return@navLambda // Exit lambda
            }
            Log.d("ClickListenerDebug", "performNavigation: URI is valid: $uriString")

            try {
                Log.d("ClickListenerDebug", "performNavigation: Inside try block.")

                // Create the Bundle with arguments (names must match those in navigation.xml)
                val arguments = Bundle().apply {
                    putLong("internalId", internalIdForNav)
                    putString("imageUriString", uriString)
                }
                Log.d("ClickListenerDebug", "performNavigation: Arguments created: $arguments")

                // Find the NavController associated with the Fragment hosting this RecyclerView
                // We use holder.itemViewContainer which is the root view of the list item layout
                val navController =  holder.itemView.findNavController()

                // Navigate using the action ID defined in navigation.xml
                // Pass the arguments Bundle
                navController.navigate(R.id.action_searchFragment_to_imageFragment, arguments)

                Log.d("ClickListenerDebug", "performNavigation: navController.navigate() called.")

            } catch (e: IllegalStateException) {
                // Catch specific exception if NavController is not found (e.g., view not attached)
                Log.e("ClickListenerDebug", "performNavigation: Could not find NavController. Is the view attached?", e)
                Toast.makeText(context, "Navigation Error (Controller)", Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                // Catch specific exception if action ID is invalid
                Log.e("ClickListenerDebug", "performNavigation: Invalid action ID or arguments.", e)
                Toast.makeText(context, "Navigation Error (Action)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Catch general exceptions
                Log.e("ClickListenerDebug", "performNavigation: Exception during navigation.", e)
                Toast.makeText(context, "Error opening image.", Toast.LENGTH_SHORT).show()
            }
            Log.d("ClickListenerDebug", "<== performNavigation finished.")
        }

        holder.checkBox.setOnClickListener {
            // Toggle selection using internalId
            if (selectionTracker.isSelected(itemInternalId)) {
                selectionTracker.deselect(itemInternalId)
            } else {
                selectionTracker.select(itemInternalId)
            }
        }

        // --- Image Click Handling ---
        holder.imageView.setOnClickListener {
            if (selectionTracker.hasSelection()) {
                // If selection is active, toggle selection
                if (selectionTracker.isSelected(itemInternalId)) {
                    selectionTracker.deselect(itemInternalId)
                } else {
                    selectionTracker.select(itemInternalId)
                }
            } else {
                // Navigate using the determined URI and internalId
                performNavigation(imageUriToLoad, itemInternalId) // Call adapted performNavigation
            }
        }

        // --- Image Long Click Handling ---
        holder.imageView.setOnLongClickListener {
            if (!selectionTracker.hasSelection()) {
                selectionTracker.select(itemInternalId)
                return@setOnLongClickListener true // Consume the long click
            }
            false // Don't consume if selection already active
        }

        // Add SuppressLint for the warning we are structurally addressing

        holder.enlargeButton.setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Ask parent not to intercept
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    // Return false: We handled the flag, but let event continue
                    false
                }

                MotionEvent.ACTION_UP -> {
                    // Call performClick when touch gesture completes UP
                    // This triggers the OnClickListener and accessibility events
                    view.performClick()
                    // Reset the disallow flag
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    // Return true: Indicate we handled the UP here in the touch listener context
                    // (Though performClick runs the OnClickListener logic)
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    // Reset the disallow flag
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    false
                }

                else -> {
                    // Don't consume other events like MOVE
                    false
                }
            }
        }


        // Set the OnClickListener for the enlarge button
        holder.enlargeButton.setOnClickListener { clickedView ->
            Log.d(
                "ClickListenerDebug",
                "EnlargeButton OnClickListener triggered for item internalId $itemInternalId"
            )
            // Call the adapted navigation logic
            performNavigation(imageUriToLoad, itemInternalId)
        }
        // *** END Listener Setup ***


        // Checkbox visibility based on selection mode
        if (selectionTracker.hasSelection()) {
            holder.checkBox.visibility = View.VISIBLE
        } else {
            holder.checkBox.visibility = View.GONE
        }
    }
        // getItemId MUST return the stable ID used by SelectionTracker - the internalId
        override fun getItemId(position: Int): Long {
            // Check bounds to prevent crash if dataset is modified unexpectedly
            return if (position >= 0 && position < dataset.size) {
                dataset[position].internalId
            } else {
                RecyclerView.NO_ID // Return invalid ID if position is out of bounds
            }
        }


}