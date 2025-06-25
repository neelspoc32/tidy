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
import com.slavabarkov.tidy.R
import com.slavabarkov.tidy.utils.PageIndicatorView
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

//        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
//            override fun onPageSelected(position: Int) {
//                super.onPageSelected(position)
//                currentIndex = position
//            }
//        })
        return inflater.inflate(R.layout.fragment_full_screen_gallery, container, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        findNavController().previousBackStackEntry
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPager)
        val indicatorView = view.findViewById<PageIndicatorView>(R.id.pageIndicator)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = imageUris.size
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
        fun showAndAutoHideIndicator(position: Int) {
            indicatorView.clearAnimation()
            indicatorView.animate()
                .alpha(1f)
                .setDuration(150)
                .start()
            indicatorView.update(position, imageUris.size)

            // Cancel any pending hide
            indicatorView.removeCallbacks(null)

            // Auto-hide after 3 seconds
            indicatorView.postDelayed({
                indicatorView.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .start()
            }, 3000)
        }
        // Initial show
        showAndAutoHideIndicator(startIndex)
        viewPager.setCurrentItem(startIndex, false)
        indicatorView.update(startIndex, imageUris.size)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                 super.onPageSelected(position)
                 currentIndex = position
                findNavController().previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("scrollToIndex", currentIndex)
                showAndAutoHideIndicator(position)
            }
        })

    }

}
