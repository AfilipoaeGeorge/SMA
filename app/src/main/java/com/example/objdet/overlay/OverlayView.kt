package com.example.objdet.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * View transparent desenat peste PreviewView. Primeste lista de [OverlayBox] (coordonate in view)
 * si deseneaza pentru fiecare: un dreptunghi verde (bounding box) si deasupra eticheta
 * cu numele obiectului si scorul in procente. La setarea [detections] se apeleaza [postInvalidate].
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boxPaint = Paint().apply {
        // contur dreptunghi bounding box
        color = DARK_GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        // text eticheta + procent deasupra box-ului
        color = 0xFFFFFFFF.toInt()
        textSize = 32f
        isAntiAlias = true
        setShadowLayer(2f, 0f, 0f, 0xFF000000.toInt())
    }

    private val textBgPaint = Paint().apply {
        // fundal dreptunghi sub text pentru lizibilitate
        color = DARK_GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 220
    }

    private val textBounds = Rect()

    /**
     * Lista de detectii in coordonate view (pixeli pe ecran). La setare se apeleaza [postInvalidate]
     * si view-ul se redeseneaza cu noile dreptunghiuri si etichete.
     */
    var detections: List<OverlayBox> = emptyList()
        set(value) {
            field = value
            postInvalidate()
        }

    /**
     * Deseneaza peste canvas: pentru fiecare [OverlayBox] un contur verde (bounding box),
     * un fundal verde sub text si eticheta "nume procent%" deasupra box-ului.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (d in detections) {
            val r = d.viewRect
            canvas.drawRect(r, boxPaint)
            val label = "${d.label} ${(d.score * 100).toInt()}%"
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            val pad = 4f
            val bgLeft = r.left
            val bgTop = r.top - textBounds.height() - pad * 2
            val bgRight = r.left + textBounds.width() + pad * 2
            val bgBottom = r.top
            canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, textBgPaint)
            canvas.drawText(label, r.left + pad, r.top - pad, textPaint)
        }
    }

    companion object {
        /** Culoarea verde inchis pentru conturul bounding box si fundalul etichetei. */
        private const val DARK_GREEN = 0xFF1B5E20.toInt()
    }
}
