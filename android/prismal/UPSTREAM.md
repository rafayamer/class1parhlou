# Vendored: PrismalAGSL (liquid glass for Jetpack Compose)

- Upstream: https://github.com/styropyr0/PrismalAGSL
- Licence: the repository's `LICENSE` file is **MIT** (copyright 2026 Saurav Sajeev), but the individual source
  files carry **Apache-2.0** headers (copyright 2025 Styropyr0). Both are permissive and allow commercial use.
  To satisfy both: keep `LICENSE` and every file header, mark modified files, and list changes below.
- Commit: `b0ba5fef4a1560e6c75a6d3421807fa33526af09` (library version 1.0.7, 2026-11-12)
- Copied: the `Prismal/src/main` library module only (no demo app, no upstream tests).

## Local changes

Record every change made to the vendored code here so it can be re-synced with upstream.

- Build file rewritten for this project's Gradle setup (no version catalog, AGP 8.13, compileSdk 36).
- Added `PrismalInit.kt` with public `initializePrismal(context)`, so the app can load shader assets
  itself; upstream throws if App Startup's provider has not run (for example under Robolectric).
