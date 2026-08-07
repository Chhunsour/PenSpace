# 🖊️ PenSpace — S Pen Thinking & Planning Workspace for Android

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![ML Kit](https://img.shields.io/badge/AI-ML_Kit_Digital_Ink-FBBC04?style=for-the-badge&logo=google&logoColor=white)](https://developers.google.com/ml-kit/vision/digital-ink-recognition)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)

**PenSpace** is a high-performance, native Android thinking, planning, and note-taking workspace optimized specifically for the **Samsung Galaxy S23 Ultra** and **S Pen** / Android stylus devices.

Designed as a lightweight combination of Samsung Notes, an infinite whiteboard, and a structured planning canvas.

---

## ✨ Features

- 🖋️ **Ultra-Low Latency Freehand Ink**: Custom hardware-accelerated stylus engine extracting sub-pixel historical digitizer events (`getHistoricalX/Y/Pressure`) for 120Hz refresh.
- 📐 **Vector Shapes & "Draw & Hold" Auto-Clean**: Draw rough lines, arrows, rectangles, or circles and hold the S Pen tip at the end to auto-convert them into clean vector shapes.
- 🔍 **Focal-Point Canvas Navigation**: Figma-style 0.2x to 10.0x zoom and pan anchored dynamically around gesture focal points in World Space ($x_{canvas}, y_{canvas}$).
- 🔘 **S Pen Side Button Temporary Eraser**: Press and hold the physical S Pen side button to temporarily erase ink without changing your selected toolbar tool.
- 🔤 **On-Device Handwriting Recognition**: Powered by Google ML Kit Digital Ink Recognition to convert handwritten notes into editable text.
- 🪄 **Lasso Selection**: Freehand polygon selection loop to move, scale, duplicate, delete, or convert handwriting.
- 📝 **Typed Text & Highlighters**: Semi-transparent highlighter brush and editable typed text elements.
- 🎨 **Canvas Background Patterns**: Plain, Dots, Grid (Graph Paper), and Line notebook backgrounds.
- 💾 **Local Persistence**: Debounced JSON persistence saving strokes, vector shapes, text, zoom scale, and canvas state locally.

---

## 🏗️ Architecture

```
/Users/macbook/Documents/Android App/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/spen/canvas/
│       │   │   ├── MainActivity.kt
│       │   │   ├── geometry/
│       │   │   │   └── ShapeRecognizer.kt    # "Draw & Hold" shape auto-straightener
│       │   │   ├── ml/
│       │   │   │   └── HandwritingRecognizer.kt # ML Kit Digital Ink engine
│       │   │   ├── model/
│       │   │   │   ├── DrawingElements.kt    # InkStroke, ShapeElement, TextElement
│       │   │   │   ├── LassoSelection.kt     # Polygon ray-casting & bounding box
│       │   │   │   └── CanvasDocument.kt     # Persistence snapshot model
│       │   │   ├── repository/
│       │   │   │   └── CanvasRepository.kt   # JSON Autosave repository
│       │   │   └── ui/
│       │   │       ├── CanvasScreen.kt        # Jetpack Compose workspace UI & dock
│       │   │       ├── DrawingViewModel.kt    # StateFlow & Undo/Redo history
│       │   │       └── canvas/
│       │   │           └── StylusCanvasView.kt# Hardware-accelerated S Pen view
│       │   └── res/values/themes.xml
│       └── test/java/com/spen/canvas/
│           └── StrokeModelTest.kt             # Unit test suite
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🛠️ Development Environment & Requirements

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
*Generated APK output location:*
`app/build/outputs/apk/debug/app-debug.apk`

### 4. Install on Samsung Galaxy S23 Ultra via ADB
```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for details.
