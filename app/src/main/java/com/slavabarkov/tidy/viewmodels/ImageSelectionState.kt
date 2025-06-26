package com.slavabarkov.tidy.viewmodels

import androidx.lifecycle.LiveData

interface ImageSelectionState {
    val selectedItemIds: LiveData<Set<Long>>
    fun saveSelection(newSet: Set<Long>)
}
