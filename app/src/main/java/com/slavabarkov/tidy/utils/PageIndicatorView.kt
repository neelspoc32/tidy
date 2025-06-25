package com.slavabarkov.tidy.utils

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.slavabarkov.tidy.R

class PageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val textView: TextView = TextView(context).apply {
        textSize = 16f
        setPadding(16, 10, 16, 10)
        setTextColor(ContextCompat.getColor(context, android.R.color.white))
        setBackgroundResource(R.drawable.bg_page_indicator)
        gravity = Gravity.CENTER
    }

    init {
        val params = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )
        addView(textView, params)
        visibility = View.GONE
    }

    fun update(currentIndex: Int, totalCount: Int) {
        textView.text = "${currentIndex + 1} of $totalCount"
        visibility = if (totalCount > 1) View.VISIBLE else View.GONE
    }
}
