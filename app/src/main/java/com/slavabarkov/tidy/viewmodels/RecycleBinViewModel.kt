package com.slavabarkov.tidy.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RecycleBinViewModel : ViewModel(), ImageSelectionState {

    private val _selectedItemIds = MutableLiveData<Set<Long>>(emptySet())
    override val selectedItemIds: LiveData<Set<Long>>
        get() = _selectedItemIds

    // identical helper APIs
    override fun saveSelection(newSet: Set<Long>) {
        _selectedItemIds.value = newSet
    }
    fun clearSelection()            { _selectedItemIds.value = emptySet() }

    /** index that the gallery tells us to scroll to */
    val scrollTarget = MutableLiveData<Int?>()
}
