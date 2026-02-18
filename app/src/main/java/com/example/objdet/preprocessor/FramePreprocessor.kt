package com.example.objdet.preprocessor

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pregateste frame-uri din CameraX (ImageProxy YUV) pentru inferenta.
 * [imageProxyToBitmap]: YUV → JPEG → Bitmap RGB cu rotire corecta.
 * [bitmapToFloatBuffer]: resize la dimensiunea modelului si RGB → ByteBuffer float32 [0,1] (pentru modele custom).
 * Pentru ObjectDetector din TFLite Task Vision se foloseste doar [imageProxyToBitmap]; modelul face resize intern.
 *
 * @param modelInputWidth Latimea la care se redimensioneaza pentru float buffer (implicit 300).
 * @param modelInputHeight Inaltimea la care se redimensioneaza pentru float buffer (implicit 300).
 */
class FramePreprocessor(
    private val modelInputWidth: Int = 300,
    private val modelInputHeight: Int = 300
) {
    private val outputBuffer: ByteBuffer by lazy {
        ByteBuffer.allocateDirect(4 * modelInputWidth * modelInputHeight * 3).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    /**
     * Converteste un frame ImageProxy in ByteBuffer float32 RGB normalizat [0,1] pentru inferenta custom.
     * Reutilizeaza [outputBuffer]; nu folosi bufferul returnat pe alt thread fara copiere.
     *
     * @param imageProxy Frame-ul de la CameraX (YUV_420_888).
     * @return ByteBuffer cu valorile R,G,B [0,1] interleaved, dimensiune modelInputWidth x modelInputHeight.
     */
    fun preprocess(imageProxy: ImageProxy): ByteBuffer {
        val bitmap = imageProxyToBitmap(imageProxy) ?: return outputBuffer
        return bitmapToFloatBuffer(bitmap)
    }

    /**
     * Converteste ImageProxy (YUV_420_888) in Bitmap RGB cu orientare corecta.
     * Citeste planele Y si VU, formeaza NV21, comprima in JPEG, decodeaza in Bitmap, apoi
     * aplica rotirea din [ImageProxy.imageInfo.rotationDegrees] ca preview-ul sa corespunda (ex. 90°).
     *
     * @param imageProxy Frame-ul de la CameraX; trebuie sa fie YUV_420_888.
     * @return Bitmap RGB rotit corect sau null daca formatul nu e YUV_420_888 sau decodarea esueaza.
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        if (imageProxy.format != ImageFormat.YUV_420_888) return null
        val yBuffer = imageProxy.planes[0].buffer
        val vuBuffer = imageProxy.planes[2].buffer
        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()
        val nv21 = ByteArray(ySize + vuSize)
        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
        val jpegBytes = out.toByteArray()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: return null
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    /**
     * Redimensioneaza [bitmap] la modelInputWidth x modelInputHeight, apoi scrie
     * canalele R, G, B normalizate [0,1] in [outputBuffer] (float32, RGB interleaved).
     * Bitmap-ul scalat este reciclat daca e diferit de cel original.
     *
     * @param bitmap Imaginea sursa (se poate redimensiona).
     * @return [outputBuffer] rewind-uit; contine 3 * width * height float-uri.
     */
    fun bitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, modelInputWidth, modelInputHeight, true)
        outputBuffer.rewind()
        val pixels = IntArray(modelInputWidth * modelInputHeight)
        scaled.getPixels(pixels, 0, modelInputWidth, 0, 0, modelInputWidth, modelInputHeight)
        for (i in pixels.indices) {
            val p = pixels[i]
            outputBuffer.putFloat(((p shr 16) and 0xFF) / 255f)
            outputBuffer.putFloat(((p shr 8) and 0xFF) / 255f)
            outputBuffer.putFloat((p and 0xFF) / 255f)
        }
        if (scaled != bitmap) scaled.recycle()
        outputBuffer.rewind()
        return outputBuffer
    }
}
