# Scanner module — origin

Adapted from **sammyZi/pdf-tools** (MIT licence, Copyright (c) 2026 SammyZi):
<https://github.com/sammyZi/pdf-tools>, commit `6a48d60ccbbaca4dd18840b9d85f00d44b51f311`.
The upstream licence is kept in `LICENSE-pdf-tools`.

Only the document scanner was taken. No PDF tools, file manager, OCR, Google sign-in or QR reading.

| File here | Upstream file | Changes |
|---|---|---|
| `vision/PageDetector.kt` | `PageDetector.kt` | Package name only. |
| `vision/DocModel.kt` | `DocModel.kt` | Package name; the model bytes come from `DocVision.init(context)` instead of a global app context. |
| `vision/DocVision.kt` | `DocVision.kt` | Kept detection, upright loading, perspective crop, quarter turns and the Auto colour / Light text / Grayscale filters. Removed strokes, brightness/contrast, Whiteboard filter and PDF auto-crop. Loads files, not content URIs. |
| `DocCamera.kt` | `DocCamera.kt` | Removed QR reading, the front lens and flash modes; photos go to a folder the caller gives; preview shows the whole 3:4 frame so the outline lines up. |
| `ScannerFlow.kt` | `Scanner.kt`, `Review.kt` (ideas only) | Rewritten for Parhlou: camera with live outline and steady auto-capture (tracker logic from upstream), review with crop, turn, filter, reorder, retake, delete and **match each page to a question**. |
| `PageStore.kt` | `ScanModel.kt` (ideas only) | Rewritten: one store per paper, JSON on app-private storage, page-to-question matching, 40-page limit. |
| `src/main/assets/docaligner_lc100.onnx` | same path | Unchanged (upstream's OpenCV-compatible export of DocAligner LC100). |

## Third-party parts used by this module

| Project | Used for | Licence |
|---|---|---|
| [OpenCV](https://opencv.org/) `org.opencv:opencv:4.12.0` | Page detection, crop and filters | Apache-2.0 |
| [CameraX](https://developer.android.com/media/camera/camerax) 1.6.0 | Camera preview and capture | Apache-2.0 |
| [DocAligner LC100](https://github.com/DocsaidLab/DocAligner) by DocsaidLab | Finds the four page corners | Apache-2.0 |
| Jetpack Compose, AndroidX | UI | Apache-2.0 |

## Not yet tested on a real phone

Robolectric cannot run the camera or OpenCV's native library. Page storage, matching and the review screen are
tested; capture, edge detection and filters must be checked on a real phone (low-end phones especially).
