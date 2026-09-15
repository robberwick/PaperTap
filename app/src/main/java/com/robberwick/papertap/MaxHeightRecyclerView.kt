package com.robberwick.papertap

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView that honors `android:maxHeight`, which the base class silently
 * ignores. Caps the measured height at [maxHeightPx]; width is untouched.
 */
class MaxHeightRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    private var maxHeightPx: Int

    init {
        context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.maxHeight)).apply {
            maxHeightPx = getDimensionPixelSize(0, DEFAULT_MAX_HEIGHT_DP * resources.displayMetrics.density.toInt())
            recycle()
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val mode = View.MeasureSpec.getMode(heightSpec)
        var spec = heightSpec
        if (mode == View.MeasureSpec.UNSPECIFIED || View.MeasureSpec.getSize(spec) > maxHeightPx) {
            spec = View.MeasureSpec.makeMeasureSpec(
                maxHeightPx,
                if (mode == View.MeasureSpec.EXACTLY) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST,
            )
        }
        super.onMeasure(widthSpec, spec)
    }

    companion object {
        private const val DEFAULT_MAX_HEIGHT_DP = 400
    }
}
