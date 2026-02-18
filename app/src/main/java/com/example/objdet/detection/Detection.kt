package com.example.objdet.detection

import android.graphics.RectF

/**
 * Rezultat al unei detectari: eticheta obiectului, scor de incredere [0,1] si dreptunghiul
 * bounding box. Sistemul de coordonate (ecran, bitmap sau buffer) depinde de unde e folosit.
 *
 * @param label Numele clasei detectate.
 * @param score Increderea in intervalul [0, 1].
 * @param boundingBox Dreptunghiul (left, top, right, bottom) in coordonatele contextului curent.
 */
data class Detection(
    val label: String,
    val score: Float,
    val boundingBox: RectF
)
