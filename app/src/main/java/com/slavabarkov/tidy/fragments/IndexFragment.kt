/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.slavabarkov.tidy.fragments

import android.Manifest // Added Manifest import
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build // Added Build import
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.slavabarkov.tidy.viewmodels.ORTImageViewModel
import com.slavabarkov.tidy.R
import com.slavabarkov.tidy.utils.PreferencesHelper
import com.slavabarkov.tidy.viewmodels.ProcessingStatus // Import ProcessingStatus

class IndexFragment : Fragment() {
    private lateinit var progressBar: ProgressBar
    private lateinit var statusTextView: TextView
    private lateinit var indexButton: Button
    private lateinit var navigateButton: Button

    private val mORTImageViewModel: ORTImageViewModel by activityViewModels()
    // *** NEW UI Elements ***
    private lateinit var selectedFolderTextView: TextView
    private lateinit var selectFolderButton: Button
    private lateinit var clearFolderButton: Button
    // *********************

    // *** NEW: Activity Result Launcher for Folder Picker ***
    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>
    // ******************************************************

    // Permission Launcher
    private lateinit var permissionsRequest: ActivityResultLauncher<String>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the folder picker launcher
        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val contentResolver = requireActivity().contentResolver
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)

                    PreferencesHelper.saveSelectedFolderUri(requireContext(), uri)
                    updateSelectedFolderUI(uri)
                    Toast.makeText(context, R.string.folder_selected_toast, Toast.LENGTH_SHORT).show()
                    // Optionally prompt user to clear index or handle automatically
                    // Consider showing a dialog here instead of auto-clearing
                    // mORTImageViewModel.clearAllEmbeddings { ... }
                } catch (e: SecurityException) {
                    Log.e("IndexFragment", "Failed to take persistable URI permission", e)
                    Toast.makeText(context, R.string.error_persisting_permission, Toast.LENGTH_LONG).show()
                } catch(e: Exception) {
                    Log.e("IndexFragment", "Error processing selected folder URI", e)
                    Toast.makeText(context, R.string.error_selecting_folder, Toast.LENGTH_LONG).show()
                }
            } else {
                Log.d("IndexFragment", "Folder selection cancelled or failed.")
            }
        }

        // Initialize the permission launcher
        permissionsRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission granted - DO NOT start indexing automatically here.
                // Just enable the button if needed.
                indexButton.isEnabled = true // Ensure button is enabled
                Log.d("IndexFragment", "Storage permission granted via callback.")
            } else {
                // Permission denied
                indexButton.isEnabled = false // Disable button
                Toast.makeText(context, "Storage permission is required to index images.", Toast.LENGTH_LONG).show()
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_index, container, false)
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Find UI elements
        progressBar = view.findViewById(R.id.progressBar)
        statusTextView = view.findViewById(R.id.textProcessingStatus)
        indexButton = view.findViewById(R.id.buttonIndex)
        navigateButton = view.findViewById(R.id.buttonNavigateToSearch)
        selectedFolderTextView = view.findViewById(R.id.selectedFolderTextView)
        selectFolderButton = view.findViewById(R.id.selectFolderButton)
        clearFolderButton = view.findViewById(R.id.clearFolderButton)

        // Request required permissions when view is created
        // This will trigger the permissionsRequest callback defined in onCreate
        requestAppropriatePermission()


        // --- START: MODIFIED Observer Logic ---
        mORTImageViewModel.mProcessingStatus.observe(viewLifecycleOwner) { status: ProcessingStatus ->
            // Update status text and button enabled state always
            statusTextView.text = getString(status.messageResId)
            val buttonsEnabled = !status.isProcessing
            indexButton.isEnabled = buttonsEnabled
            selectFolderButton.isEnabled = buttonsEnabled
            clearFolderButton.isEnabled = buttonsEnabled
            navigateButton.isEnabled = buttonsEnabled

            // Update progress bar based on processing state
            if (status.isProcessing) {
                // While processing, use the values from the status
                progressBar.max = status.maxProgress
                progressBar.progress = status.progress
                progressBar.isIndeterminate = (status.maxProgress <= 0) // Show indeterminate if max is unknown
            } else {
                // When NOT processing (Idle, Complete, Ready, Error)
                progressBar.isIndeterminate = false // Ensure not indeterminate
                progressBar.max = 100 // Set a fixed max (or use status.maxProgress if preferred)

                // Set progress explicitly based on the final state message
                when (status.messageResId) {
                    R.string.index_status_complete,
                    R.string.index_status_ready -> {
                        // For Complete or Ready, show 100%
                        progressBar.progress = progressBar.max
                    }
                    R.string.index_status_idle,
                    R.string.index_status_error, // Add other error message IDs if needed
                    R.string.error_loading_model,
                    R.string.error_model_not_ready,
                    R.string.error_invalid_folder -> {
                        // For Idle or Error states, show 0%
                        progressBar.progress = 0
                    }
                    else -> {
                        // Default case if a new non-processing state is added
                        // You might want 0 or 100 based on context
                        progressBar.progress = 0
                    }
                }
            }
        }
        // --- END: MODIFIED Observer Logic ---


        // Set initial state for folder selection UI
        val currentUri = PreferencesHelper.getSelectedFolderUri(requireContext())
        updateSelectedFolderUI(currentUri)

        // Set Click Listeners for folder buttons
        selectFolderButton.setOnClickListener {
            try {
                folderPickerLauncher.launch(null)
            } catch (e: Exception) {
                Log.e("IndexFragment", "Error launching folder picker", e)
                Toast.makeText(context, R.string.error_launching_picker, Toast.LENGTH_SHORT).show()
            }
        }

        clearFolderButton.setOnClickListener {
            PreferencesHelper.saveSelectedFolderUri(requireContext(), null)
            updateSelectedFolderUI(null)
            Toast.makeText(context, R.string.selection_cleared_toast, Toast.LENGTH_SHORT).show()
            // Recommend clearing index here or prompt user
            Toast.makeText(context, "Please re-index after clearing folder selection.", Toast.LENGTH_LONG).show()
            // mORTImageViewModel.clearAllEmbeddings { ... }
        }

        // Index Button Listener (Keep as is - only starts indexing on click)
        indexButton.setOnClickListener {
            // Check permission before starting
            if (hasStoragePermission()) {
                Toast.makeText(context, R.string.clearing_previous_index, Toast.LENGTH_SHORT).show()
                val currentFolderUri = PreferencesHelper.getSelectedFolderUri(requireContext())
                Toast.makeText(context, R.string.starting_new_index, Toast.LENGTH_SHORT).show()
                mORTImageViewModel.startIndexing(currentFolderUri)
            } else {
                Toast.makeText(context, "Storage permission needed to start indexing.", Toast.LENGTH_LONG).show()
                requestAppropriatePermission() // Request permission again if button clicked without it
            }
        }

        navigateButton.setOnClickListener {
            // Check if indexing is complete/ready before navigating
            val currentStatus = mORTImageViewModel.mProcessingStatus.value
            if (currentStatus?.isProcessing == true) {
                Toast.makeText(context, "Please wait for indexing to finish.", Toast.LENGTH_SHORT).show()
            } else if (mORTImageViewModel.idxList.isEmpty() && currentStatus?.messageResId != R.string.index_status_idle) {
                // Allow navigation if truly idle (never indexed), but warn if indexed but empty
                Toast.makeText(context, "No images found after indexing.", Toast.LENGTH_SHORT).show()
                // Optionally still navigate: findNavController().navigate(R.id.action_indexFragment_to_searchFragment)
            } else {
                findNavController().navigate(R.id.action_indexFragment_to_searchFragment)
            }
        }

        return view
    }

    // Helper function to update UI based on selected folder
    private fun updateSelectedFolderUI(selectedUri: Uri?) {
        if (selectedUri != null) {
            val folderName = PreferencesHelper.getFolderName(requireContext(), selectedUri) ?: selectedUri.path ?: "Selected Folder"
            selectedFolderTextView.text = getString(R.string.indexing_scope_folder, folderName)
            clearFolderButton.visibility = View.VISIBLE
        } else {
            selectedFolderTextView.text = getString(R.string.indexing_scope_all)
            clearFolderButton.visibility = View.GONE
        }
    }

    // Helper function to request the correct permission based on SDK level
    private fun requestAppropriatePermission() {
        val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        Log.d("IndexFragment", "Requesting permission: $permissionToRequest")
        permissionsRequest.launch(permissionToRequest)
    }

    // Helper function to check the correct permission based on SDK level
    private fun hasStoragePermission(): Boolean {
        val permissionToCheck = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val granted = ContextCompat.checkSelfPermission(requireContext(), permissionToCheck) == PackageManager.PERMISSION_GRANTED
        Log.d("IndexFragment", "Checking permission $permissionToCheck: $granted")
        return granted
    }

}