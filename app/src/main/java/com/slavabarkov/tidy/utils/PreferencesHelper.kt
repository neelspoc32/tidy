// e.g., com/slavabarkov/tidy/utils/PreferencesHelper.kt
package com.slavabarkov.tidy.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.slavabarkov.tidy.viewmodels.IndexingScopeStatus

object PreferencesHelper {

    private const val PREFS_NAME = "tidy_prefs"
    private const val KEY_SELECTED_FOLDER_URI = "selected_folder_uri"
    private const val KEY_INDEXING_SCOPE_STATUS = "indexing_scope_status"
    private const val KEY_LAST_INDEX_SCOPE_TYPE = "last_index_scope_type" // New key for indexing scope
    private const val KEY_LAST_INDEXED_FOLDER_NAME = "last_indexed_folder_name" // New key for last indexed folder name

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveSelectedFolderUri(context: Context, uri: Uri?) {
        getPreferences(context).edit {
            putString(KEY_SELECTED_FOLDER_URI, uri?.toString())
            // Optionally store the last indexed scope here too if needed for comparison
        }
    }

    fun getSelectedFolderUri(context: Context): Uri? {
        val uriString = getPreferences(context).getString(KEY_SELECTED_FOLDER_URI, null)
        return uriString?.let { Uri.parse(it) }
    }

    // Optional: Helper to get a displayable name from the URI
    fun getFolderName(context: Context, uri: Uri): String? {
        return try {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name
        } catch (e: Exception) {
            // Handle potential security exceptions or invalid URIs
            null
        }
    }

    /**
     * Saves the current indexing scope status.
     * @param context The application context.
     * @param status The IndexingScopeStatus to save.
     */
    fun saveIndexingScopeStatus(context: Context, status: IndexingScopeStatus) {
        getPreferences(context).edit {
            putString(KEY_INDEXING_SCOPE_STATUS, status.name) // Save enum name as string
            Log.d("PreferencesHelper", "Saved Indexing Scope Status: ${status.name}") // Log for debugging
        }
    }

    /**
     * Retrieves the saved indexing scope status.
     * Defaults to NONE if no status is found or if the stored value is invalid.
     * @param context The application context.
     * @return The saved IndexingScopeStatus, or NONE if not found.
     */
    fun getIndexingScopeStatus(context: Context): IndexingScopeStatus {
        val statusString = getPreferences(context).getString(KEY_INDEXING_SCOPE_STATUS, null)
        return try {
            val status = statusString?.let { IndexingScopeStatus.valueOf(it) } ?: IndexingScopeStatus.NONE
            Log.d("PreferencesHelper", "Retrieved Indexing Scope Status: $status (from stored: $statusString)") // Log for debugging
            status
        } catch (e: IllegalArgumentException) {
            // This catches cases where a stored string doesn't match an enum value (e.g., enum changed)
            Log.e("PreferencesHelper", "Invalid Indexing Scope Status stored: $statusString. Defaulting to NONE.", e) // Log error
            IndexingScopeStatus.NONE
        }
    }

    fun getLastIndexScope(context: Context): IndexingScopeStatus {
        val statusString = getPreferences(context).getString(KEY_LAST_INDEX_SCOPE_TYPE, null)
        return try {
            statusString?.let { IndexingScopeStatus.valueOf(it) } ?: IndexingScopeStatus.NONE
        } catch (e: IllegalArgumentException) {
            // Handle cases where the stored string is not a valid enum name
            IndexingScopeStatus.NONE
        }
    }

    fun saveLastIndexedFolderName(context: Context, folderName: String?) {
        getPreferences(context).edit {
            putString(KEY_LAST_INDEXED_FOLDER_NAME, folderName)
        }
    }

    fun getLastIndexedFolderName(context: Context): String? {
        return getPreferences(context).getString(KEY_LAST_INDEXED_FOLDER_NAME, null)
    }
    /**
     * Clears all indexing related preferences.
     * Use with caution, usually only when all indexed data is removed.
     */
    fun clearAllIndexingPreferences(context: Context) {
        getPreferences(context).edit {
            remove(KEY_SELECTED_FOLDER_URI)
            remove(KEY_LAST_INDEX_SCOPE_TYPE)
            remove(KEY_LAST_INDEXED_FOLDER_NAME)
        }
    }
}
