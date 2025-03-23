package io.github.yagiyuu.autoclicker.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import io.github.yagiyuu.autoclicker.util.Utils

class ClickPointView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val dotRadius = Utils.dpToPx(context, 4).toFloat()
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = Utils.dpToPx(context, 8).toFloat()
        isAntiAlias = true
    }
    private val dotPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radius = width / 2f
        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawCircle(centerX, centerY, radius - paint.strokeWidth / 2, paint)
        canvas.drawCircle(centerX, centerY, dotRadius, dotPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = Utils.dpToPx(context, 48)
        setMeasuredDimension(size, size)
    }
}