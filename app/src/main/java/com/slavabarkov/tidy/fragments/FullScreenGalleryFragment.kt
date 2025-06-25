package com.slavabarkov.tidy.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.slavabarkov.tidy.viewmodels.SearchViewModel

class FullScreenGalleryFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var imageUris: List<String>
    private lateinit var internalIds: List<Long>
    private var startIndex: Int = 0
    private var currentIndex: Int = 0
    private var selectionModeEnabled: Boolean = false
    private val sharedViewModel: SearchViewModel by activityViewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            imageUris = it.getStringArray("imageUris")?.toList() ?: emptyList()
            internalIds = it.getLongArray("internalIds")?.toList() ?: emptyList()
            startIndex = it.getInt("startIndex", 0)
            selectionModeEnabled = it.getBoolean("selectionModeEnabled", false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewPager = ViewPager2(requireContext())
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = imageUris.size

            override fun createFragment(position: Int): Fragment {
                return ImageFragment().apply {
                    arguments = Bundle().apply {
                        putString("imageUriString", imageUris[position])
                        putLong("internalId", internalIds[position])
                        putBoolean("selectionModeEnabled", selectionModeEnabled)
                    }
                }
            }
        }
        viewPager.setCurrentItem(startIndex, false)
        currentIndex = startIndex

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentIndex = position
            }
        })
        return viewPager
    }

    override fun onDestroyView() {
        super.onDestroyView()
        findNavController().previousBackStackEntry
        sharedViewModel.scrollTarget.value = currentIndex
    }
}
