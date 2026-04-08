package com.example.arcomtechapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SignaturePadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 80, 80, 80)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val path = Path()
    private var hasSignatureStroke: Boolean = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        val baseline = height - paddingBottom - 24f
        canvas.drawLine(paddingLeft.toFloat(), baseline, width - paddingRight.toFloat(), baseline, guidePaint)
        canvas.drawPath(path, strokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                hasSignatureStroke = true
            }
            MotionEvent.ACTION_MOVE -> path.lineTo(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> path.lineTo(x, y)
        }
        invalidate()
        return true
    }

    fun clearSignature() {
        path.reset()
        hasSignatureStroke = false
        invalidate()
    }

    fun hasSignature(): Boolean = hasSignatureStroke

    fun renderBitmap(): Bitmap {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        return Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            draw(canvas)
        }
    }
}
