package com.example.objdet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.objdet.camera.CameraController
import com.example.objdet.detector.ObjectDetectorWrapper
import com.example.objdet.overlay.OverlayBox
import com.example.objdet.overlay.OverlayView
import com.example.objdet.ui.theme.ObjDetTheme
import com.example.objdet.Constants

/**
 * Activitate principala: cere permisiunea de camera, apoi afiseaza CameraScreen (preview +
 * overlay cu bounding box-uri, numar obiecte, slider precizie).
 */
class MainActivity : ComponentActivity() {

    private val cameraPermissionGranted = mutableStateOf(false)

    /**
     * Contract pentru cererea permisiunii CAMERA. La raspuns, actualizeaza [cameraPermissionGranted]
     * si logheaza rezultatul (acordat/refuzat).
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraPermissionGranted.value = isGranted
        if (isGranted) Log.d(TAG, "Camera permission granted")
        else Log.w(TAG, "Camera permission denied")
    }

    /**
     * Initializeaza activitatea: verifica permisiunea CAMERA, activeaza edge-to-edge,
     * seteaza tema si continutul (CameraScreen daca e permis, altfel mesaj). Lanseaza
     * cererea de permisiune daca nu e deja acordata.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraPermissionGranted.value =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
        }
        setContent {
            ObjDetTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val granted by cameraPermissionGranted
                    when {
                        granted -> CameraScreen(activity = this@MainActivity)
                        else -> Text("Acorda permisiunea pentru camera.")
                    }
                }
            }
        }
        if (!cameraPermissionGranted.value) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    companion object {
        /** Tag pentru log-uri in aceasta activitate. */
        private const val TAG = "MainActivity"
    }
}

/**
 * Ecranul cu preview camera, overlay-ul de detectii, numarul de obiecte (colt dreapta sus)
 * si bara de precizie (jos). threshold e salvat cu rememberSaveable ca sa ramana la rotire.
 * displayed = detectii filtrate dupa threshold (score >= threshold). Numar obiecte in colt dreapta sus.
 *
 * Ordinea logicii: (1) incarcare detector pe background; (2) dupa ce e gata, creare CameraController
 * o singura data; (3) bind camera in AndroidView.update o singura data (flag bound); (4) cleanup
 * doar in DisposableEffect(Unit) la iesirea din ecran - nu folosi chei care se schimba (ex. view ref)
 * altfel onDispose ruleaza prematur si opreste executorul inainte de bind.
 */
/** Intervalul maxim (ms) intre actualizari ale overlay-ului pe UI (~15 fps) pentru a nu incarca main thread-ul. */
private const val OVERLAY_UPDATE_INTERVAL_MS = 66L

/**
 * Ecran principal cu preview camera, overlay de detectii si controale.
 * Ordinea: (1) incarca detectorul pe background; (2) creeaza [CameraController] o data ce modelul e gata;
 * (3) leaga camera in [AndroidView] o singura data (flag [bound]); (4) cleanup doar in DisposableEffect(Unit)
 * la iesirea din ecran (cheie Unit ca sa nu ruleze onDispose prematur).
 * Afiseaza numarul de obiecte (filtrat dupa [threshold]) in coltul dreapta sus si slider-ul de precizie jos.
 */
@androidx.compose.runtime.Composable
private fun CameraScreen(activity: ComponentActivity) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var detections by remember { mutableStateOf(emptyList<OverlayBox>()) }
    var detector by remember { mutableStateOf<ObjectDetectorWrapper?>(null) }
    var detectorLoadFailed by remember { mutableStateOf(false) }
    val detectorLoadStarted = remember { mutableStateOf(false) }
    var cameraController by remember { mutableStateOf<CameraController?>(null) }
    var bound by remember { mutableStateOf(false) }
    var threshold by rememberSaveable { mutableStateOf(Constants.DEFAULT_SCORE_THRESHOLD) }

    // Incarcare detector pe un thread de background o singura data (evita blocarea main thread).
    androidx.compose.runtime.DisposableEffect(activity) {
        if (detector != null || detectorLoadFailed || detectorLoadStarted.value) return@DisposableEffect onDispose { }
        detectorLoadStarted.value = true
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            try {
                val d = ObjectDetectorWrapper(
                    activity,
                    modelAssetName = Constants.MODEL_PATH,
                    maxResults = 20,
                    scoreThreshold = Constants.MIN_THRESHOLD
                )
                mainHandler.post { detector = d }
            } catch (e: Exception) {
                Log.e("CameraScreen", "ObjectDetectorWrapper init failed", e)
                mainHandler.post { detectorLoadFailed = true }
            }
        }.start()
        onDispose { }
    }

    if (detector == null) {
        Box(Modifier.fillMaxSize()) {
            Text(
                if (detectorLoadFailed) "Model lipsa. Adauga ${Constants.MODEL_PATH} in app/src/main/assets/"
                else "Se incarca modelul..."
            )
        }
        return
    }

    // Controller-ul se creeaza o singura data dupa ce detector-ul e gata; foloseste acelasi lifecycleOwner.
    if (cameraController == null) {
        cameraController = CameraController(
            context = activity,
            lifecycleOwner = lifecycleOwner,
            detector = detector!!,
            onDetections = { list, _ -> detections = list },
            overlayUpdateIntervalMs = OVERLAY_UPDATE_INTERVAL_MS
        )
    }

    val displayed = detections.filter { it.score >= threshold }

    // Cleanup doar cand iesim din ecran (cheie Unit = nu depinde de view/ref). Daca am folosi
    // previewViewRef.value ca cheie, la prima setare a view-ului onDispose al efectului anterior
    // ar rula si ar apela unbind() + executor.shutdown() inainte de bind, si nu s-ar mai vedea detectii.
    DisposableEffect(Unit) {
        onDispose {
            cameraController?.unbind()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Bind camera o singura data cand avem view-ul si controller-ul; evita re-legarea la fiecare recompozitie.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                androidx.camera.view.PreviewView(ctx).apply {
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FIT_CENTER
                }
            },
            update = { previewView ->
                if (!bound && cameraController != null) {
                    cameraController?.bindToPreview(previewView)
                    bound = true
                }
            }
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> OverlayView(ctx) },
            update = { overlayView -> overlayView.detections = displayed }
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "${displayed.size}",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxSize()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Precizie: ${(threshold * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "${(Constants.MIN_THRESHOLD * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    valueRange = Constants.MIN_THRESHOLD..Constants.MAX_THRESHOLD,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(Constants.MAX_THRESHOLD * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
