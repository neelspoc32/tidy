
package com.slavabarkov.tidy.fragments

// RecycleBinFragment.kt

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.slavabarkov.tidy.R
import com.slavabarkov.tidy.adapters.ImageAdapter
import com.slavabarkov.tidy.adapters.ImageItemDetailsLookup
import com.slavabarkov.tidy.adapters.ImageItemKeyProvider
import com.slavabarkov.tidy.data.ImageEmbedding
import com.slavabarkov.tidy.utils.FastScrollHelper
import com.slavabarkov.tidy.utils.GalleryNavigationHelper
import com.slavabarkov.tidy.viewmodels.RecycleBinViewModel

@RequiresApi(Build.VERSION_CODES.R)
class RecycleBinFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ImageAdapter
    private lateinit var selectionTracker: SelectionTracker<Long>
    private lateinit var restoreButton: Button
    private lateinit var deleteButton: Button
    private lateinit var imageCountText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateText: TextView
    private lateinit var selectAllCheckBox: CheckBox
    private lateinit var restoreResultLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var deleteResultLauncher: ActivityResultLauncher<IntentSenderRequest>
    private val mRecycleBinVM: RecycleBinViewModel by activityViewModels()
    private var trashedItems = mutableListOf<ImageEmbedding>()
    private var isFirstLoad = true
    companion object {
        private var lastSelectedIds: List<Long>? = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_recycle_bin, container, false)
        recyclerView = view.findViewById(R.id.recycler_view_trash)
        restoreButton = view.findViewById(R.id.restoreButton)
        deleteButton = view.findViewById(R.id.deleteButton)
        imageCountText = view.findViewById(R.id.imageCountText)
        progressBar = view.findViewById(R.id.progressBar)
        emptyStateText = view.findViewById(R.id.emptyStateText)
        selectAllCheckBox = view.findViewById(R.id.selectAllCheckBox)

        val scrollThumb = view.findViewById<View>(R.id.custom_scroll_thumb)
        val scrollZone = view.findViewById<View>(R.id.scroll_zone)

        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            findNavController().popBackStack()
        }

        FastScrollHelper.setupEdgeScrollZone(recyclerView, scrollZone, scrollThumb)
        adapter = ImageAdapter(requireContext(), trashedItems, { image ->
            val bundle = GalleryNavigationHelper.buildGalleryArgs(
                trashedItems,
                image,
                selectedIds = mRecycleBinVM.selectedItemIds.value ?: emptySet()
            )

            if (bundle != null) {
                findNavController().navigate(R.id.action_recycleBinFragment_to_fullScreenGalleryFragment, bundle)
            } else {
                Toast.makeText(requireContext(), "Could not open image", Toast.LENGTH_SHORT).show()
            }
        }, isFromRecycleBin = true)

        recyclerView.adapter = adapter
        //trashedItems = loadTrashedImages()
        selectionTracker = SelectionTracker.Builder(
            "recycle-bin-selection",
            recyclerView,
            ImageItemKeyProvider(adapter),
            ImageItemDetailsLookup(recyclerView),
            StorageStrategy.createLongStorage()
        ).withSelectionPredicate(SelectionPredicates.createSelectAnything()).build()

        adapter.selectionTracker = selectionTracker

        selectionTracker.addObserver(object : SelectionTracker.SelectionObserver<Long>() {
            override fun onSelectionChanged() {
                val selected = selectionTracker.selection.toSet()
                mRecycleBinVM.saveSelection(selected)  // triggers LiveData
            }
        })
        mRecycleBinVM.selectedItemIds.value?.let { previouslySelected ->
            if (previouslySelected.isNotEmpty()) {
                selectionTracker.setItemsSelected(previouslySelected, true)
            }
        }
//        selectAllCheckBox.setOnCheckedChangeListener { _, isChecked ->
//            if (isChecked) {
//                trashedItems.forEach { selectionTracker.select(it.contentId) }
//            } else {
//                selectionTracker.clearSelection()
//            }
//        }

        restoreResultLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                removeRestoredItemsFromList()
                lastSelectedIds = null
                Toast.makeText(context, "Images restored successfully.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Restore cancelled.", Toast.LENGTH_SHORT).show()
            }
        }

        deleteResultLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                removeRestoredItemsFromList()
                lastSelectedIds = null
                Toast.makeText(context, "Images deleted permanently.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Deletion cancelled.", Toast.LENGTH_SHORT).show()
            }
        }

        if (isFirstLoad) {
            trashedItems = loadTrashedImages()
            isFirstLoad = false
        }
        lastSelectedIds?.forEach { id ->
            selectionTracker.select(id)
        }

        restoreButton.setOnClickListener {
            val selectedIds = selectionTracker.selection.toList()
            val urisToRestore = trashedItems.filter { selectedIds.contains(it.contentId) }.mapNotNull {
                it.mediaStoreId?.let { id -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id) }
            }
            if (urisToRestore.isNotEmpty()) {
                try {
                    val request = MediaStore.createTrashRequest(requireContext().contentResolver, urisToRestore, false)
                    val intentSender = IntentSenderRequest.Builder(request).build()
                    restoreResultLauncher.launch(intentSender)
                    //trashedItems.removeAll { it.contentId in selectedIds }
                    //adapter.updateData(trashedItems)
                } catch (e: Exception) {
                    Log.e("RecycleBinFragment", "Restore failed", e)
                    Toast.makeText(context, "Error during restore.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "No images selected to restore.", Toast.LENGTH_SHORT).show()
            }
        }

        deleteButton.setOnClickListener {
            val selectedIds = selectionTracker.selection.toList()
            val urisToDelete = trashedItems.filter { selectedIds.contains(it.contentId) }.mapNotNull {
                it.mediaStoreId?.let { id -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id) }
            }
            if (urisToDelete.isNotEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Permanently")
                    .setMessage("Are you sure you want to permanently delete ${urisToDelete.size} image(s)?")
                    .setPositiveButton("Delete") { _, _ ->
                        try {
                            val request = MediaStore.createDeleteRequest(requireContext().contentResolver, urisToDelete)
                            val intentSender = IntentSenderRequest.Builder(request).build()
                            deleteResultLauncher.launch(intentSender)
                        } catch (e: Exception) {
                            Log.e("RecycleBinFragment", "Delete failed", e)
                            Toast.makeText(context, "Error during delete.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(context, "No images selected to delete.", Toast.LENGTH_SHORT).show()
            }
        }
        // Observe scrollToIndex sent back from FullScreenGalleryFragment
        findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<Int>("scrollToIndex")
            ?.observe(viewLifecycleOwner) { index ->
                mRecycleBinVM.scrollTarget.value = index
            }

        mRecycleBinVM.selectedItemIds.observe(viewLifecycleOwner) {
            updateSelectionUi()
        }

        mRecycleBinVM.scrollTarget.observe(viewLifecycleOwner) { index ->
            index?.let {
                val layoutManager = recyclerView.layoutManager as? GridLayoutManager
                layoutManager?.scrollToPositionWithOffset(index, 100) // offset from top in pixels
                mRecycleBinVM.scrollTarget.value = null // consume
            }
        }


//        val selectAllCheckBox = view.findViewById<CheckBox>(R.id.selectAllCheckBox)
//        selectAllCheckBox.setOnCheckedChangeListener { _, isChecked ->
//            if (isChecked) {
//                trashedItems.forEach { selectionTracker.select(it.contentId) }
//            } else {
//                selectionTracker.clearSelection()
//            }
//        }

        return view
    }

    override fun onResume() {
        super.onResume()
        lastSelectedIds?.forEach { id ->
            selectionTracker.select(id)
        }
//        if (!isFirstLoad) {
//            loadTrashedImages()
//        }
    }
    private fun loadTrashedImages(): MutableList<ImageEmbedding> {
        progressBar.isVisible = true
        emptyStateText.isVisible = false
        trashedItems.clear()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_EXPIRES
        )
        val queryArgs = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        val uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        requireContext().contentResolver.query(uri, projection, queryArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val expiresColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val date = cursor.getLong(dateCol) * 1000
                val expiresAtValue = cursor.getLong(expiresColumn) * 1000
                val daysLeft = ((expiresAtValue - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                trashedItems.add(
                    ImageEmbedding(
                        internalId = -id,
                        mediaStoreId = id,
                        documentUri = null,
                        date = date,
                        embedding = FloatArray(0),
                    ).apply {
                        expiresAt = expiresAtValue
                    }
                )
            }
        }
        if (::adapter.isInitialized) {
            adapter.updateData(trashedItems)
//            lastSelectedIds?.forEach { id ->
//                selectionTracker.select(id)
//            }
//            lastSelectedIds = null
            val idsToRestore = lastSelectedIds
            lastSelectedIds = null
            if (::selectionTracker.isInitialized) {
                idsToRestore?.forEach { selectionTracker.select(it) }
            }
        }
        imageCountText.text = "${trashedItems.size} Images"
        emptyStateText.isVisible = trashedItems.isEmpty()
        progressBar.isVisible = false
        return trashedItems
    }

    private fun removeRestoredItemsFromList() {
        val selectedIds = selectionTracker.selection.toSet()
        trashedItems.removeAll { selectedIds.contains(it.contentId) }
        adapter.updateData(trashedItems)
        selectionTracker.clearSelection()
        imageCountText.text = "0/${trashedItems.size} images selected"
        emptyStateText.isVisible = trashedItems.isEmpty()
    }

    private fun updateSelectionUi() {
        val selected = mRecycleBinVM.selectedItemIds.value?.size ?: 0
        val total = trashedItems.size

        if (selected > 0) {
            imageCountText.text = "$selected/$total images selected"
            imageCountText.visibility = View.VISIBLE
            selectAllCheckBox.visibility = View.VISIBLE
        } else {
            imageCountText.text = "${trashedItems.size} Images"
            selectAllCheckBox.visibility = View.GONE
        }

        // Set selectAllCheckBox state without triggering listener
        selectAllCheckBox.setOnCheckedChangeListener(null)
        selectAllCheckBox.isChecked = selected == total && total > 0
        selectAllCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                trashedItems.forEach { selectionTracker.select(it.contentId) }
            } else {
                selectionTracker.clearSelection()
            }
        }
    }
}
