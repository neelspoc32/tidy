package com.slavabarkov.tidy.utils

import android.content.res.Resources
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

object FastScrollHelper {

    private val Float.dp: Float
        get() = this * Resources.getSystem().displayMetrics.density

    private var hideRunnable: Runnable? = null
    private val hideDelayMillis = 1500L

    fun setupEdgeScrollZone(
        recyclerView: RecyclerView,
        scrollZone: View,
        scrollThumb: View
    ) {
        var isDragging = false

        // Initial thumb state
        scrollThumb.alpha = 0f
        scrollThumb.pivotY = 0f
        scrollThumb.pivotX = scrollThumb.width / 2f

        // Handle scroll events
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!isDragging) {
                    updateThumbPosition(rv, scrollThumb)
                    adjustThumbHeight(rv, scrollThumb)
                }
            }
        })

        scrollZone.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    scrollThumb.animate().scaleX(1.35f).scaleY(1.35f).setDuration(150).start()
                    showThumb(scrollThumb)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val rawY = event.rawY
                    val location = IntArray(2)
                    recyclerView.getLocationOnScreen(location)
                    val recyclerTop = location[1]
                    val recyclerHeight = recyclerView.height

                    val relativeY = (rawY - recyclerTop).coerceIn(0f, recyclerHeight.toFloat())
                    val proportion = relativeY / recyclerHeight

                    val itemCount = recyclerView.adapter?.itemCount ?: 0
                    val targetIndex = (itemCount * proportion).toInt()
                    recyclerView.scrollToPosition(targetIndex.coerceIn(0, itemCount - 1))

                    val scaledHeight = scrollThumb.height * scrollThumb.scaleY
                    val thumbRange = recyclerHeight - scaledHeight
                    val fudge = 2f.dp
                    val newY = (proportion * thumbRange - fudge).coerceIn(0f, thumbRange)
                    scrollThumb.translationY = newY

                    showThumb(scrollThumb)
                    scheduleHideThumb(scrollThumb)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    scrollThumb.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .withEndAction {
                            updateThumbPosition(recyclerView, scrollThumb)
                        }.start()
                    scheduleHideThumb(scrollThumb)
                    scrollThumb.performClick()
                    true
                }

                else -> false
            }
        }

        // Initial adjustment
        recyclerView.post {
            adjustThumbHeight(recyclerView, scrollThumb)
            updateThumbPosition(recyclerView, scrollThumb)
        }
    }

    fun updateThumbPosition(rv: RecyclerView, thumb: View) {
        val offset = rv.computeVerticalScrollOffset()
        val range = rv.computeVerticalScrollRange()
        val extent = rv.computeVerticalScrollExtent()

        if (range <= extent) {
            thumb.translationY = 0f
            return
        }

        val scale = thumb.scaleY
        val scaledHeight = thumb.height * scale
        val thumbRange = rv.height - scaledHeight
        val fudge = 2f.dp
        val proportion = offset.toFloat() / (range - extent).toFloat()
        val newY = (proportion * thumbRange - fudge).coerceIn(0f, thumbRange)
        thumb.translationY = newY

        showThumb(thumb)
        scheduleHideThumb(thumb)
    }

    fun adjustThumbHeight(recyclerView: RecyclerView, thumb: View) {
        val extent = recyclerView.computeVerticalScrollExtent()
        val range = recyclerView.computeVerticalScrollRange()

        if (range <= 0) return

        val proportion = extent.toFloat() / range
        val minThumbHeight = 48f.dp
        val maxThumbHeight = 300f.dp  // Optional visual cap
        val thumbHeight = (recyclerView.height * proportion).coerceIn(minThumbHeight, maxThumbHeight)
        Log.d("FastScroll", "Thumb height: $thumbHeight, Proportion: $proportion")

        val layoutParams = thumb.layoutParams
        if (layoutParams.height != thumbHeight.toInt()) {
            thumb.animate()
                .setDuration(100)
                .withStartAction {
                    layoutParams.height = thumbHeight.toInt()
                    thumb.layoutParams = layoutParams
                }
        }
    }

    private fun scheduleHideThumb(thumb: View) {
        hideRunnable?.let { thumb.removeCallbacks(it) }

        hideRunnable = Runnable {
            thumb.animate()
                .alpha(0f)
                .setDuration(300)
                .start()
        }

        thumb.postDelayed(hideRunnable!!, hideDelayMillis)
    }

    private fun showThumb(thumb: View) {
        thumb.animate()
            .alpha(1f)
            .setDuration(150)
            .start()
        hideRunnable?.let { thumb.removeCallbacks(it) }
    }
}
