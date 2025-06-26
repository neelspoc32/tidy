/**
 * Copyright 2023 Viacheslav Barkov
 */

package com.slavabarkov.tidy.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.slavabarkov.tidy.dot

class SearchViewModel(application: Application) : AndroidViewModel(application), ImageSelectionState {
    var searchResults: List<Long>? = null
    var fromImg2ImgFlag: Boolean = false
    private val _selectedItemIds = MutableLiveData<Set<Long>>(emptySet())
    val scrollTarget = MutableLiveData<Int?>()

    override val selectedItemIds: LiveData<Set<Long>>
        get() = this._selectedItemIds


    fun sortByCosineDistance(searchEmbedding: FloatArray,
                          imageEmbeddingsList: List<FloatArray>,
                          imageIdxList: List<Long>) {
        val distances = LinkedHashMap<Long, Float>()
        for (i in imageEmbeddingsList.indices) {
            val dist = searchEmbedding.dot(imageEmbeddingsList[i])
            distances[imageIdxList[i]] = dist
        }
        searchResults = distances.toList().sortedBy { (k, v) -> v }.map { (k, v) -> k }.reversed()
    }
    // Function to update selected IDs from SearchFragment
    override fun saveSelection(newSet: Set<Long>) {
        _selectedItemIds.value = newSet
    }
    // Function to clear saved selection (e.g., on clear button)
    fun clearSavedSelection() {
        _selectedItemIds.value = emptySet()
    }

    // For tracking long UI operations like delete/move
    private val _uiOperationInProgress = MutableLiveData<Boolean>(false)
    val uiOperationInProgress: LiveData<Boolean> = _uiOperationInProgress

    fun setUiOperationInProgress(isInProgress: Boolean) {
        _uiOperationInProgress.postValue(isInProgress)
    }

}