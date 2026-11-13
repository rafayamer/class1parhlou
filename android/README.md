# Parhlou student app (Android)

Kotlin 2.2 + Jetpack Compose (BOM 2026.06), Material 3, single activity, minimum Android 7.1 (API 25). Glass bars come from the vendored PrismalAGSL library in `prismal/` (see `prismal/UPSTREAM.md`). **Milestone 1 foundation with offline sample data.** It is not connected to the backend, has no sign-in, billing, scanner or AI marking yet.

## Prototype features (demo brain, no real AI yet)

- **AI Tutor** (`ai/DemoBrain.kt`, `ui/screens/TutorScreens.kt`): chat that answers from 12 sample notes on Maths 9 Unit 1 in the Luna structure (answer, explanation, steps, source label, check question), with "Explain more simply / Another example / Show each step / Ask me a question". It checks the answer to its own question, refuses what it doesn't know, stays inside the student's subjects and is locked while a paper is running. Opens from Home, a module, a wrong MCQ and a Check Master result.
- **Chapter test** (`ui/screens/TestScreens.kt`): a real 40-mark Unit 1 paper: 10 MCQs, 8 short (answer 6), 3 long (answer 2). Typed answers and page photos (photo picker, stored privately) save as you go.
- **Check Master** (`domain/CheckMaster.kt`, `ui/screens/CheckMasterScreens.kt`): marks MCQs exactly and typed answers criterion by criterion with what/why/how. Unrecognised answers are "Couldn't check automatically" and photos "Waiting for the AI examiner", never zero. Every question can be retyped and re-checked; the first result is kept. The same checker marks written practice in lessons.
- **Invigilator** (`domain/Invigilator.kt`): logs start, leaving, returning and hand-in, then reports time used and time away. Report only, on the phone only.
- **Saved progress**: evidence is an append-only log on the phone. Demo-checked papers never count as verified.
- **GPT-6 Luna** (`ai/LunaGateway.kt`): used when the app is built with `-PparhlouAiUrl` and `-PsupabaseAnonKey`; otherwise, or when offline, the demo brain answers and says so. The OpenAI key is never in the app. See `supabase/README.md`.

Limits: the demo brain is rule-based and only knows the Unit 1 sample content. Photo picking and leave-the-app tracking have not been tried on a real phone.

## Live updates

- **Content** (`data/RemoteContent.kt`, `data/ContentBundle.kt`): the app downloads the published bundle on open and when it returns to the front, keeps a copy for offline use, and falls back to the packed-in sample.
- **App builds** (`.github/workflows/android.yml`, `update/AppUpdater.kt`): every push builds an APK and publishes a release `build-<n>`; the app shows "Update available" on Home and Profile and installs with one tap. Builds are signed with the shared prototype key in `keys/`.
- Prototype only: remove `REQUEST_INSTALL_PACKAGES`, the updater and the committed key before a Play Store release.

## Screens

- Welcome and the four-step setup (PRD §4): data notice, board/class/medium/subjects (unreleased boards and subjects shown as "Coming later"), pathway (Complete Academy / Test Series), study time with an honest feasibility message.
- Home (PRD §5): greeting, Ask a question / book / scan shortcuts, a dark "Do this now" card with one Continue action and an assessed-progress ring, week strip, tiles and today's tasks. Floating pill bar: Home, Book, Papers, Profile.
- Tutor screen (input switched off until sourced answers exist) and Profile with real counts only, plan card, parent link and "Clear this phone".
- Subject → units (current unit expanded) → module with the five-stage rail (PRD §7): Learn (YouTube link-out only; empty state when no checked video), Quiz (rationale for every option, first try kept), Book (references only, no unverified page numbers), Write (autosaves on the phone), Improve.
- Test Series (PRD §11): readiness check gates the papers; paper start screen validates that marks add up; 90-minute style timer that keeps running if the app is closed; honest "marking pending" result.
- Progress states from PRD §9. The Journey rocket counts only assessed modules.

Sample content is written by Parhlou for development, labelled "Sample — demo content", and contains no textbook text. Unit names follow the new Punjab Class 9 Maths book as reported by third-party sources and are unverified.

The visual theme (`ui/theme/Theme.kt`) is the "Calm Green" design in light and dark (decision D-010, see `docs/design/`). Screenshots: `docs/design/screens/`.

## Build and test

Requires JDK 17+ and the Android SDK (platform 36).

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

If Robolectric can't download its Android runtime jars (restricted networks), download the `android-all-instrumented` 12.1, 14 and 15 jars from Maven Central into a folder and run:

```bash
./gradlew :app:testDebugUnitTest -ProbolectricDir=/path/to/jars
```
