package com.example.objdet.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions

/**
 * Rezultat brut al unei detectari: eticheta obiectului, scor de incredere [0,1] si dreptunghiul
 * bounding box in coordonate bitmap (pixeli), in acelasi sistem ca Bitmap-ul de intrare.
 */
data class DetectedBox(
    val label: String,
    val score: Float,
    val box: RectF
)

/**
 * Incarca modelul TFLite de tip Object Detection si ruleaza detectia pe un [Bitmap].
 * Coordonatele din [DetectedBox.box] sunt in acelasi sistem de coordonate ca Bitmap-ul
 * de intrare (pentru mapare ulterioara la buffer si view cu [bitmapRectToBufferRect] + CoordinateTransform).
 *
 * @param context Context pentru incarcarea asset-ului.
 * @param modelAssetName Calea fisierului .tflite in assets.
 * @param maxResults Numarul maxim de detectii returnate per frame.
 * @param scoreThreshold Prag minim de incredere pentru detectii (filtrate la nivel model).
 */
class ObjectDetectorWrapper(
    context: Context,
    modelAssetName: String,
    maxResults: Int = 5,
    scoreThreshold: Float = 0.5f
) {
    private val detector: ObjectDetector

    /** Initializeaza optiunile TFLite si creeaza detectorul din fisierul model din assets. */
    init {
        val options = ObjectDetectorOptions.builder()
            .setMaxResults(maxResults)
            .setScoreThreshold(scoreThreshold)
            .build()

        detector = ObjectDetector.createFromFileAndOptions(context, modelAssetName, options)
    }

    /**
     * Ruleaza detectia pe [bitmap]. Converteste Bitmap → TensorImage, apeleaza modelul TFLite,
     * apoi mapeaza fiecare detectie la [DetectedBox] (eticheta cea mai probabila, scor, RectF in pixeli bitmap).
     *
     * @param bitmap Imaginea pe care se ruleaza detectia (coordonatele box sunt relative la acest bitmap).
     * @return Lista de detectii; trebuie mapate ulterior la buffer/view cu [bitmapRectToBufferRect] + CoordinateTransform.
     */
    fun detect(bitmap: Bitmap): List<DetectedBox> {
        val image = TensorImage.fromBitmap(bitmap)
        val detections: List<Detection> = detector.detect(image)
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()

        return detections.mapNotNull { det ->
            val best = det.categories.maxByOrNull { it.score } ?: return@mapNotNull null
            val b = det.boundingBox
            DetectedBox(
                label = best.label,
                score = best.score,
                box = RectF(b.left, b.top, b.right, b.bottom)
            )
        }
    }
}
