# ObjDet – Real-Time Object Detection on Android

Aplicație Android pentru detectare obiecte în timp real folosind camera dispozitivului, TensorFlow Lite și Jetpack Compose. Aplicația afișează bounding box-uri și etichete peste preview-ul camerei și permite ajustarea pragului de încredere.

---

## Demo Concept

- Camera preview live
- Detectare obiecte pe fiecare frame
- Bounding box-uri desenate peste imagine
- Etichete cu numele obiectului și procentul de încredere
- Slider pentru ajustarea preciziei

---

## Funcționalități

- Camera preview prin CameraX
- Detectare obiecte în timp real
- Overlay custom pentru bounding box-uri
- Ajustare prag încredere între 20% și 95%
- Contor obiecte detectate
- Procesare asincronă fără blocarea UI
- Curățare corectă a resurselor la închiderea ecranului

---

## Arhitectură și Flux

### 1. Permisiuni

**MainActivity:**
- Cere permisiunea CAMERA
- După acordare, afișează CameraScreen (Jetpack Compose)

---

### 2. Încărcare model

- Modelul `.tflite` este citit din `app/src/main/assets`
- Inițializarea se face pe thread de background
- Se folosește `ObjectDetectorWrapper`

Dacă modelul lipsește:

```
Model lipsa. Adauga fisierul .tflite in app/src/main/assets/
```

---

### 3. Pipeline procesare frame

**CameraController:**
- CameraX Preview + ImageAnalysis

Pentru fiecare frame:

```
ImageProxy (YUV)
→ conversie la Bitmap
→ rotire corectă
→ trimitere la detector
```

Rezultatul:

```
listă DetectedBox(label, score, RectF)
```

---

### 4. Transformare coordonate

Flux coordonate:

```
Bitmap → Buffer → View
```

- Detectarea produce coordonate în spațiul bitmap
- `bitmapRectToBufferRect()` mapează la buffer ImageProxy
- `CoordinateTransform` (CameraX) mapează la coordonate view
- Rezultatul este listă de OverlayBox

---

### 5. Overlay

`OverlayView` desenează:
- Dreptunghiuri verzi
- Text: label + procent
- Contor obiecte

Actualizarea UI este limitată la aproximativ 15 FPS pentru a preveni lag vizual.

---

## Modele TFLite

### Model principal utilizat

```
efficientdet-tflite-lite4-detection-metadata.tflite
```

### Model alternativ

```
efficientdet-tflite-lite1-detection-metadata.tflite
```

- Lite1 este mai rapid
- Lite4 este mai precis

---

## Unde se găsesc modelele

Modelele EfficientDet Lite pot fi descărcate de aici:

Lite1:
https://www.kaggle.com/models/tensorflow/efficientdet/tfLite/lite1-detection-metadata

Lite4:
https://www.kaggle.com/models/tensorflow/efficientdet/tfLite/lite4-detection-metadata

Modelele fac parte din familia EfficientDet optimizată pentru dispozitive mobile.

---

## Cum sunt folosite modelele

### Biblioteci utilizate

- tensorflow-lite-task-vision 0.4.4
- tensorflow-lite-support-api 0.4.4

### Inițializare

```kotlin
ObjectDetector.createFromFileAndOptions(...)
```

### Setări

```
maxResults = 20
scoreThreshold = 0.2f
```

XNNPACK delegate este activ automat pentru optimizare CPU.

---

## Structura proiectului

```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   ├── efficientdet-tflite-lite1-detection-metadata.tflite
│   └── efficientdet-tflite-lite4-detection-metadata.tflite
├── java/com/example/objdet/
│   ├── MainActivity.kt
│   ├── Constants.kt
│   ├── camera/
│   │   ├── CameraController.kt
│   │   └── CoordinateMapping.kt
│   ├── detector/
│   │   └── ObjectDetector.kt
│   ├── overlay/
│   │   ├── OverlayBox.kt
│   │   └── OverlayView.kt
│   ├── preprocessor/
│   │   └── FramePreprocessor.kt
│   └── ui/theme/
```

---

## Tehnologii

- Kotlin
- Jetpack Compose
- CameraX 1.3.4
- TensorFlow Lite Task Vision
- EfficientDet Lite
- XNNPACK CPU delegate

---

## Cerințe

- Android Studio
- minSdk 24
- Dispozitiv cu cameră
- Model `.tflite` plasat în:

```
app/src/main/assets/
```

---

## Build

### Build debug

```
./gradlew assembleDebug
```

### Instalare pe device

```
./gradlew installDebug
```

---

## Utilizare

1. Deschide aplicația
2. Acordă permisiunea pentru cameră
3. Îndreaptă camera către obiecte
4. Ajustează sliderul pentru precizie
5. Observă bounding box-urile și contorul

---

## Performanță

- Rulează cu XNNPACK delegate pe CPU
- FPS depinde de model:
  - Lite1 mai rapid
  - Lite4 mai precis
- Update UI limitat pentru stabilitate vizuală

---

## Licență

Modelele EfficientDet Lite sunt furnizate de TensorFlow si respecta licenta Apache 2.0.

Codul aplicației este pentru scop educațional.
