package com.example.objdet.overlay

import android.graphics.RectF

/**
 * O singura detectare pregatita pentru afisare pe overlay: eticheta obiectului, scorul de incredere [0,1]
 * si dreptunghiul in coordonate view (pixeli pe ecran). [viewRect] este deja transformat din buffer
 * cu CoordinateTransform, deci poate fi desenat direct peste PreviewView in [OverlayView].
 *
 * @param label Numele clasei detectate (ex. "person", "car").
 * @param score Increderea modelului in intervalul [0, 1].
 * @param viewRect Bounding box in coordonate view (left, top, right, bottom).
 */
data class OverlayBox(
    val label: String,
    val score: Float,
    val viewRect: RectF
)
