# 🖊️ GalaxyPen — Transparent S Pen Thinking & Planning Workspace for Android

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![ML Kit](https://img.shields.io/badge/AI-ML_Kit_Digital_Ink-FBBC04?style=for-the-badge&logo=google&logoColor=white)](https://developers.google.com/ml-kit/vision/digital-ink-recognition)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

**GalaxyPen** is a modern, high-performance, open-source native Android workspace designed for handwriting, drawing, diagrams, and freeform thinking. Optimized specifically for the **Samsung Galaxy S23 Ultra** and **S Pen** / Android stylus devices.

GalaxyPen combines the speed of immediate handwriting with the power of infinite whiteboard canvases, vector shape recognition, and first-class editable canvas image objects.

---

## ✨ Features

- 🖋️ **Ultra-Low Latency S Pen Engine**: Hardware-accelerated custom view utilizing sub-pixel historical digitizer events (`getHistoricalX/Y/Pressure`) and Android `MotionEventPredictor` for ultra-fast, zero-lag drawing on 120Hz AMOLED displays.
- 🏠 **Glassmorphic Workspace Homepage**:
  - **Quick Note CTA**: Instant blank canvas launch with template quick choices (`Plain`, `Grid`, `Dots`, `Lines`).
  - **Continue Working**: Horizontal carousel showing your 5 most recently edited notes with live canvas thumbnail previews.
  - **Notes Library**: Responsive grid/list view with live search, filter chips (`All`, `Favorites`, `Trash`), and dropdown sorting.
- 🖼️ **First-Class Editable Canvas Images**:
  - Insert images via modern Android Photo Picker or paste directly from system clipboard.
  - Full object manipulation: Move, scale with corner handles, free rotation, duplicate, delete, copy/cut/paste, layer order (`Bring Forward` / `Send Backward`), and **Object Locking** (to write notes over reference photos).
  - Images persist safely in app internal storage (`files/images/`) without breaking permissions.
- 📐 **Extended Smart Shape Recognition**: Draw rough lines, arrows, rectangles, circles, triangles, or diamonds and hold the S Pen tip to auto-convert into clean vector shapes.
- 🧽 **Universal Eraser**: Erases freehand strokes, highlighter marks, shape objects, typed text, and unlocked images consistently without ghost artifacts.
- 🔘 **Momentary S Pen Side Button Eraser**: Press and hold the physical S Pen button (or flip to eraser tip) to temporarily erase on the fly without changing your active tool.
- 🔤 **On-Device ML Kit Handwriting Recognition**: Powered by Google ML Kit Digital Ink Recognition to convert handwritten notes into editable text elements on device.
- 🪄 **Lasso Selection & Multi-Object Transforms**: Polygon selection loop to move, scale, rotate, duplicate, delete, convert, or lock canvas elements.
- 👁️ **Focus Mode & Adaptive UI Fading**: Toolbar automatically fades to 15% opacity while writing to maximize screen area for creative focus.
- 🎨 **Canvas Paper Styles & Themes**: `Charcoal`, `White`, `Paper`, `OLED True Black` canvas surfaces with dark and light app UI theme modes.

---

## 🏗️ Project Architecture

```
GalaxyPen/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/spen/canvas/
│       │   │   ├── MainActivity.kt
│       │   │   ├── geometry/
│       │   │   │   └── ShapeRecognizer.kt         # "Draw & Hold" vector shape recognizer
│       │   │   ├── ml/
│       │   │   │   └── HandwritingRecognizer.kt    # ML Kit Digital Ink recognition engine
│       │   │   ├── model/
│       │   │   │   ├── DrawingElements.kt         # InkStroke, ShapeElement, TextElement, ImageElement
│       │   │   │   ├── LassoSelection.kt          # Polygon ray-casting & bounding box
│       │   │   │   ├── CanvasDocument.kt          # Document persistence snapshot
│       │   │   │   └── AppSettings.kt            # Appearance & S Pen preferences
│       │   │   ├── repository/
│       │   │   │   └── CanvasRepository.kt        # JSON autosave & internal image store
│       │   │   └── ui/
│       │   │       ├── CanvasScreen.kt             # Floating glass toolbar & Compose workspace
│       │   │       ├── DrawingViewModel.kt         # StateFlow & Undo/Redo snapshot history
│       │   │       ├── canvas/
│       │   │       │   └── StylusCanvasView.kt     # Hardware-accelerated S Pen canvas view
│       │   │       ├── home/
│       │   │       │   ├── NoteHomeScreen.kt       # Glassmorphic homepage & library grid/list
│       │   │       │   └── NoteThumbnailGenerator.kt # Dual-level cached canvas thumbnail renderer
│       │   │       └── theme/
│       │   │           └── AppColors.kt           # Color palettes & theme resolver
│       │   └── res/values/themes.xml
│       └── test/java/com/spen/canvas/
│           └── StrokeModelTest.kt                  # Unit test suite
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🛠️ Requirements & Environment

- **Android Studio**: Ladybug / Koala (2024.1+) or newer
- **JDK**: Java 17 (bundled with Android Studio JBR)
- **Target SDK**: Android 15 (API 35) | **Min SDK**: Android 8.0 (API 26)
- **Gradle**: 8.7 (Wrapper included)

---

## 🚀 Getting Started

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
*Output APK location:*
`app/build/outputs/apk/debug/app-debug.apk`

### 4. Install on Samsung Device via ADB
```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🤝 Contributing

Contributions are welcome! Please feel free to open an issue or submit a pull request:
1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for details.
