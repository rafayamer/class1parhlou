# Parhlou student app (Android)

Kotlin + Jetpack Compose, Material 3, single activity. **Milestone 1 foundation with offline sample data.** It is not connected to the backend, has no sign-in, billing, scanner or AI marking yet.

## What's in it

- Welcome and the four-step setup (PRD §4): data notice, board/class/medium/subjects (unreleased boards and subjects shown as "Coming later"), pathway (Complete Academy / Test Series), study time with an honest feasibility message.
- Home (PRD §5): greeting, Ask a question / book / scan shortcuts, a dark "Do this now" card with one Continue action and an assessed-progress ring, week strip, tiles and today's tasks. Floating pill bar: Home, Book, Papers, Profile.
- Tutor screen (input switched off until sourced answers exist) and Profile with real counts only, plan card, parent link and "Clear this phone".
- Subject → units (current unit expanded) → module with the five-stage rail (PRD §7): Learn (YouTube link-out only; empty state when no checked video), Quiz (rationale for every option, first try kept), Book (references only, no unverified page numbers), Write (autosaves on the phone), Improve.
- Test Series (PRD §11): readiness check gates the papers; paper start screen validates that marks add up; 90-minute style timer that keeps running if the app is closed; honest "marking pending" result.
- Progress states from PRD §9. The Journey rocket counts only assessed modules.

Sample content is written by Parhlou for development, labelled "Sample — not reviewed", and contains no textbook text. Unit names follow the new Punjab Class 9 Maths book as reported by third-party sources and are unverified.

The visual theme (`ui/theme/Theme.kt`) is the "Calm Green" design in light and dark (decision D-010, see `docs/design/`). Screenshots: `docs/design/screens/`.

## Build and test

Requires JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

If Robolectric can't download its Android runtime jars (restricted networks), download `android-all-instrumented` 14 and 15 jars from Maven Central into a folder and run:

```bash
./gradlew :app:testDebugUnitTest -ProbolectricDir=/path/to/jars
```
