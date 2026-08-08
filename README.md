<div align="center">

  <h1>🖊️ GalaxyPen</h1>
  <p><strong>Ultra-Low Latency S Pen Thinking, Sketching & Planning Workspace for Android</strong></p>

  <p>
    <em>Created with ❤️ by <strong>Chhunsour</strong></em>
  </p>

  <p>
    <a href="https://github.com/Chhunsour/PenSpace"><img src="https://img.shields.io/badge/Created_By-Chhunsour-7B2CBF?style=for-the-badge&logo=github&logoColor=white" alt="Created By Chhunsour" /></a>
    <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android_15_(API_35)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform" /></a>
    <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin_2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" /></a>
    <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" /></a>
    <a href="https://developers.google.com/ml-kit/vision/digital-ink-recognition"><img src="https://img.shields.io/badge/AI-ML_Kit_Digital_Ink-FBBC04?style=for-the-badge&logo=google&logoColor=white" alt="ML Kit" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-007ACC.svg?style=for-the-badge" alt="License" /></a>
  </p>

</div>

---

> [!NOTE]
> **GalaxyPen** is a native, high-performance Android drawing and note-taking workspace created by **[Chhunsour](https://github.com/Chhunsour)**. It is engineered to combine zero-lag stylus responsiveness with powerful freeform canvas tools, hardware-accelerated image objects, vector shape auto-recognition, and on-device AI handwriting recognition.

---

## 🌟 Key Features & Capabilities

### 🖊️ High-Performance Pen Engine
- **Hardware-Accelerated Low-Latency Canvas**: Custom `StylusCanvasView` extracting sub-pixel historical digitizer coordinates (`getHistoricalX/Y/Pressure`) for smooth, real-time stroke rendering.
- **Stroke Prediction**: Integrates Android `MotionEventPredictor` to eliminate input lag on 120Hz AMOLED displays.
- **Physical S Pen Side-Button Integration**: Press and hold the S Pen button for momentary erasing without switching active tools.

### 📐 Smart Tools & Object Manipulation
- **Draw & Hold Shape Vectorization**: Automatic recognition of straight lines, arrows, rectangles, circles, triangles, and diamonds.
- **Lasso Selection Tool**: Freeform polygon selection loop to transform, move, scale, rotate, duplicate, delete, or lock stroke clusters and canvas objects.
- **First-Class Image Objects**: Insert reference images via Android Photo Picker or system clipboard. Supports free scaling, rotation, z-index layering (`Bring Forward` / `Send Backward`), and **Object Locking** for annotating directly over photos.
- **Universal Eraser**: Cleanly erases freehand pen, highlighter marks, shape vectors, text elements, and unlocked images without ghost artifacts.

### 🧠 On-Device AI & Note Organization
- **Google ML Kit Digital Ink Recognition**: Convert handwritten notes into editable text elements directly on device.
- **Glassmorphic Homepage Workspace**:
  - **Quick Start Canvas**: Launch blank notes immediately with instant paper templates (`Plain`, `Grid`, `Dots`, `Lines`).
  - **Recent Notes Carousel**: Fast access to your 5 most recently updated notes with dual-level cached live thumbnail previews.
  - **Notes Library Grid & List Views**: Search, filter by tags (`All`, `Favorites`, `Trash`), and sort notes by title or last updated date.
- **Focus Mode & Adaptive UI Fading**: Floating toolbars automatically dim to 15% opacity while writing to maximize creative canvas space.
- **Custom Canvas Themes**: Choose between `Charcoal`, `Classic White`, `Vintage Paper`, and `OLED True Black` surfaces with dark and light app UI modes.

---

## 📸 Feature Overview Table

| Feature Category | Highlights & Capabilities | Tech Stack / API |
| :--- | :--- | :--- |
| **Stylus Engine** | Sub-pixel sampling, Pressure Sensitivity, Motion Prediction | Custom View, `MotionEventPredictor` |
| **Shape AI** | Smart "Draw & Hold" vector recognition for 6+ shape types | Custom Vector Geometry Engine |
| **Handwriting AI** | On-Device ML Ink-to-Text conversion | Google ML Kit Digital Ink |
| **Image Canvas** | Clipboard paste, photo picker, layering, z-index, object lock | Internal File Storage & Canvas Matrices |
| **State & Persistence** | Autosave, snapshot undo/redo history, JSON document state | StateFlow, Gson, Internal Storage |
| **UI & Workspace** | Glassmorphic design, focus fading, dark/light themes, template switcher | Jetpack Compose & Material 3 |

---

## 🏗️ System Architecture & Codebase Map

```
GalaxyPen/
├── app/
│   ├── build.gradle.kts                      # Dependency & SDK configuration (API 35)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/spen/canvas/
│       │   │   ├── MainActivity.kt            # Edge-to-edge entry point
│       │   │   ├── geometry/
│       │   │   │   └── ShapeRecognizer.kt     # "Draw & Hold" vector shape engine
│       │   │   ├── ml/
│       │   │   │   └── HandwritingRecognizer.kt # ML Kit Digital Ink engine
│       │   │   ├── model/
│       │   │   │   ├── DrawingElements.kt     # InkStroke, ShapeElement, TextElement, ImageElement
│       │   │   │   ├── LassoSelection.kt      # Polygon ray-casting & selection bounds
│       │   │   │   ├── CanvasDocument.kt      # Document state & serialization model
│       │   │   │   └── AppSettings.kt        # User preferences & stylus settings
│       │   │   ├── repository/
│       │   │   │   └── CanvasRepository.kt    # JSON autosave & internal storage manager
│       │   │   └── ui/
│       │   │       ├── CanvasScreen.kt        # Floating glass toolbar & Compose workspace
│       │   │       ├── DrawingViewModel.kt    # StateFlow state management & Undo/Redo history
│       │   │       ├── canvas/
│       │   │       │   └── StylusCanvasView.kt# Low-latency S Pen drawing view
│       │   │       ├── home/
│       │   │       │   ├── NoteHomeScreen.kt  # Glassmorphic homepage & library view
│       │   │       │   └── NoteThumbnailGenerator.kt # Cached canvas thumbnail renderer
│       │   │       └── theme/
│       │   │           └── AppColors.kt       # Theme palettes & surface resolvers
│       │   └── res/
│       └── test/java/com/spen/canvas/
│           └── StrokeModelTest.kt             # Unit test suite
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🛠️ Tech Stack & Dependencies

- **Language**: [Kotlin 2.0](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 & Extended Icons
- **Target SDK**: Android 15 (API level 35) | **Min SDK**: Android 8.0 (API level 26)
- **Machine Learning**: [Google ML Kit Digital Ink Recognition](https://developers.google.com/ml-kit/vision/digital-ink-recognition)
- **Stylus Input**: Android `androidx.input.motionprediction` (`MotionEventPredictor`)
- **Data & Serialization**: Google Gson for fast JSON persistence
- **Build System**: Gradle 8.7 (Kotlin DSL)

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
- **Android Studio**: Ladybug / Koala (2024.1+) or newer
- **JDK**: Java 17 (bundled with Android Studio)
- **Device**: Android 8.0+ device (Samsung Galaxy Note / S-Series with S Pen strongly recommended)

### 1. Clone the Repository
```bash
git clone https://github.com/Chhunsour/PenSpace.git
cd PenSpace
```

### 2. Run Unit Tests
```bash
./gradlew test
```

### 3. Build Debug APK
```bash
./gradlew assembleDebug
```
> The compiled APK will be located at:
> `app/build/outputs/apk/debug/app-debug.apk`

### 4. Deploy to Connected Device via ADB
```bash
# Ensure ANDROID_HOME environment variable is set
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 👤 Author & Creator

Designed, engineered, and maintained with passion by **Chhunsour**.

- 🐙 **GitHub**: [@Chhunsour](https://github.com/Chhunsour)
- 📁 **Repository**: [Chhunsour/PenSpace](https://github.com/Chhunsour/PenSpace)

> [!TIP]
> If you find this project helpful or inspiring, feel free to star ⭐ the repository on GitHub!

---

## 📜 License

Distributed under the **MIT License**. See `LICENSE` for details.

<div align="center">
  <sub>Created By <strong>Chhunsour</strong> • Built for Android with Jetpack Compose & Kotlin</sub>
</div>
