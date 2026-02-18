package com.example.objdet

/**
 * Constante globale: calea modelului TFLite in assets si limitele pentru slider-ul de precizie.
 * Folosite in [MainActivity] (CameraScreen) si la initializarea [ObjectDetectorWrapper].
 */
object Constants {
    /** Numele (sau calea) fisierului modelului TFLite in app/src/main/assets/. */
    const val MODEL_PATH = "efficientdet-tflite-lite4-detection-metadata.tflite"
    /** Prag implicit de incredere pentru afisare (doar detectii cu score >= acest prag). */
    const val DEFAULT_SCORE_THRESHOLD = 0.5f
    /** Valoarea minima a slider-ului de precizie (multe detectii, mai putin precise). */
    const val MIN_THRESHOLD = 0.2f
    /** Valoarea maxima a slider-ului (putine detectii, foarte precise). */
    const val MAX_THRESHOLD = 0.95f
}
