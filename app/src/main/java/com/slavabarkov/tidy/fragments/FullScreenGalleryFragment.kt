package com.slavabarkov.tidy.fragments

import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.slavabarkov.tidy.fragments.ImageFragment
import com.slavabarkov.tidy.R
import com.slavabarkov.tidy.utils.PageIndicatorView

class FullScreenGalleryFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var imageUris: List<String>
    private lateinit var internalIds: List<Long>
    private var startIndex: Int = 0
    private var selectionModeEnabled: Boolean = false
    private var currentIndex = 0

    private lateinit var indicatorView: PageIndicatorView

    private val fadeOutRunnable = Runnable {
        indicatorView.animate()
            .alpha(0f)
            .setDuration(300)
            .start()
    }

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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_full_screen_gallery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPager)
        indicatorView = view.findViewById(R.id.pageIndicator)

        currentIndex = startIndex

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
        showAndAutoHideIndicator(startIndex)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentIndex = position

                findNavController().previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("scrollToIndex", position)

                showAndAutoHideIndicator(position)
            }
        })

        // Optional: sync scroll when back is pressed manually
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            findNavController().previousBackStackEntry
                ?.savedStateHandle
                ?.set("scrollToIndex", currentIndex)

            findNavController().popBackStack()
        }
    }

    private fun showAndAutoHideIndicator(position: Int) {
        // Cancel previous fade-out
        indicatorView.removeCallbacks(fadeOutRunnable)

        // Update & show
        indicatorView.update(position, imageUris.size)
        indicatorView.animate()
            .cancel() // cancel previous animations just in case

        indicatorView.scaleX = 0.85f
        indicatorView.scaleY = 0.85f
        indicatorView.alpha = 0f

        indicatorView.animate()
            .alpha(1f)
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(150)
            .withEndAction {
                indicatorView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
        // Schedule fade-out
        indicatorView.postDelayed(fadeOutRunnable, 3000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        indicatorView.removeCallbacks(fadeOutRunnable)
    }
}
