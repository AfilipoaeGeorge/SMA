package com.example.objdet.camera

import android.content.Context
import android.graphics.RectF
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.objdet.detector.DetectedBox
import com.example.objdet.detector.ObjectDetectorWrapper
import com.example.objdet.overlay.OverlayBox
import com.example.objdet.preprocessor.FramePreprocessor
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.view.TransformExperimental
import java.util.concurrent.Executors

/**
  Controleaza CameraX: Preview + ImageAnalysis cu acelasi ResolutionSelector (4:3)
  ca sursa si preview sa aiba acelasi viewport si CoordinateTransform sa nu mai avertizeze.
  STRATEGY_KEEP_ONLY_LATEST pentru analiza in timp real. Flux: ImageProxy -> Bitmap ->
  detector.detect() -> bitmapRectToBufferRect -> CoordinateTransform -> OverlayBox.
  overlayUpdateIntervalMs: trimite rezultate pe main thread cel mult la acest interval (~15 fps)
  ca sa nu blocheze UI-ul cu prea multe actualizari.
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val detector: ObjectDetectorWrapper,
    private val onDetections: (List<OverlayBox>, Long) -> Unit,
    private val overlayUpdateIntervalMs: Long = 66L
) {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var latestPreviewView: PreviewView? = null
    private val preprocessor = FramePreprocessor()
    @Volatile
    private var lastOverlayPostTimeMs = 0L

    /**
      Leaga preview-ul la [previewView] si porneste analiza frame-urilor.
      Preview si ImageAnalysis folosesc acelasi ResolutionSelector (4:3) pentru acelasi viewport
      (evita avertismentul "source viewport does not match target viewport").
      Pentru fiecare frame: ImageProxy → Bitmap → detector.detect() → transformare coordonate → [onDetections].
      Rezultatele sunt postate pe main thread cel mult la [overlayUpdateIntervalMs] ms.
     */
    @OptIn(TransformExperimental::class)
    fun bindToPreview(previewView: PreviewView) {
        latestPreviewView = previewView
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val listenableFuture = ProcessCameraProvider.getInstance(context)
        listenableFuture.addListener({
            cameraProvider = listenableFuture.get()
            val provider = cameraProvider ?: return@addListener
            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        // Obligatoriu: inchidem frame-ul in finally pe orice cale (inclusiv la exceptie).
                        try {
                            val bitmap = preprocessor.imageProxyToBitmap(imageProxy)
                            if (bitmap != null) {
                                val start = System.currentTimeMillis()
                                val detections = detector.detect(bitmap)
                                val inferenceMs = System.currentTimeMillis() - start
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val bufW = imageProxy.width
                                val bufH = imageProxy.height
                                val analysisTransform: OutputTransform =
                                    ImageProxyTransformFactory().getOutputTransform(imageProxy)
                                val bufferRects = detections.map { d ->
                                    Triple(
                                        d.label,
                                        d.score,
                                        bitmapRectToBufferRect(d.box, rotation, bufW, bufH)
                                    )
                                }
                                val now = System.currentTimeMillis()
                                if (now - lastOverlayPostTimeMs >= overlayUpdateIntervalMs) {
                                    lastOverlayPostTimeMs = now
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        val pv = latestPreviewView ?: return@post
                                        if (pv.width <= 0 || pv.height <= 0) return@post
                                        val previewTransform = pv.getOutputTransform() ?: return@post
                                        val coordTransform = CoordinateTransform(analysisTransform, previewTransform)
                                        val overlayList = bufferRects.map { (label, score, bufferRect) ->
                                            val viewRect = RectF(bufferRect)
                                            coordTransform.mapRect(viewRect)
                                            OverlayBox(label = label, score = score, viewRect = viewRect)
                                        }
                                        onDetections(overlayList, inferenceMs)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Analyzer error", e)
                        } finally {
                            try {
                                imageProxy.close()
                            } catch (e: Exception) {
                                Log.e(TAG, "imageProxy.close() failed", e)
                            }
                        }
                    }
                }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Opreste toate use case-urile CameraX, opreste executorul (shutdown asteapta terminarea
     * frame-ului curent), elibereaza referintele la provider si PreviewView.
     */
    fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        latestPreviewView = null
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraController"
    }
}
