package com.toolateforteddy.tvlauncher

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView

/** An ImageView that is always 16:9, the shape of Android TV app banners. */
class BannerImageView(context: Context, attrs: AttributeSet?) : ImageView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, width * 9 / 16)
    }
}
