package com.example.objdet.camera

import android.graphics.RectF

/**
 * Converteste un dreptunghi din spatiul bitmap-ului rotit (pe care ruleaza detectorul)
 * in spatiul buffer al ImageProxy (nerotat). Rezultatul este folosit apoi de [CoordinateTransform]
 * pentru a trece din buffer in coordonate view. Bitmap-ul vine din ImageProxy dupa
 * postRotate(rotationDegrees), deci dimensiunile si orientarea difera fata de buffer.
 *
 * @param bitmapRect Dreptunghiul in coordonate bitmap (left, top, right, bottom).
 * @param rotationDegrees 0, 90, 180 sau 270 (rotirea din ImageProxy.imageInfo).
 * @param bufferWidth Latimea ImageProxy inainte de rotire.
 * @param bufferHeight Inaltimea ImageProxy inainte de rotire.
 * @return [RectF] in coordonate buffer, corespunzator aceluiasi dreptunghi vizual.
 */
fun bitmapRectToBufferRect(
    bitmapRect: RectF,
    rotationDegrees: Int,
    bufferWidth: Int,
    bufferHeight: Int
): RectF {
    val l = bitmapRect.left
    val t = bitmapRect.top
    val r = bitmapRect.right
    val b = bitmapRect.bottom
    val w = bufferWidth.toFloat()
    val h = bufferHeight.toFloat()

    return when (rotationDegrees) {
        0 -> RectF(l, t, r, b)           // fara rotire
        90 -> RectF(t, h - r, b, h - l)  // rotire 90° clockwise: left<->top, right<->bottom cu offset h
        180 -> RectF(w - r, h - b, w - l, h - t)
        270 -> RectF(w - b, t, w - t, b)
        else -> RectF(l, t, r, b)        // fallback (nu ar trebui sa apara)
    }
}
