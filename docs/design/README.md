# Design direction

**Decision D-010 (2026-11-12):** the app uses the "Calm Green" look from founder reference 1, in light and dark themes. It replaces the PRD's purple/lavender baseline.

## References (founder-supplied inspiration, third-party designs; do not copy them pixel for pixel)

- `references/reference-1-green-study-app.jpg`: palette and main layout (greeting header, quick-action pills, big rounded cards, floating pill navigation, tutor screen, profile stats).
- `references/reference-2-navy-orange-planner.jpg`: layout ideas only (dark "today's plan" card with progress ring, week strip, task tiles).

## What we deliberately did not take

| In the references | Why not | What Parhlou shows instead |
|---|---|---|
| "Unlock Study Pro / Upgrade to Pro" | One plan only (PRD §1, §26) | "Your plan" card: PKR 3,999/month, verified via Google Play |
| XP points, streak days | No manipulative streak rules (PRD §10) | Quizzes done, modules assessed, papers handed in |
| Study-hours totals | Not measured yet; no fabricated data | Nothing until tracked |
| Calendar meetings/events | No invented sessions | A plain week strip with today highlighted |

## Glass and motion (D-011)

- **Glass:** floating glass title bar on every screen and an iOS-style glass tab bar with a draggable liquid droplet, from the vendored PrismalAGSL library (`android/prismal`). It adapts per phone: frosted look on Android 7.1–11, blur on Android 12, full liquid glass (refraction, highlights) on Android 13+.
- **Motion:** sliding screen transitions, staggered entrance on Home, animated progress ring, springy button presses, sliding onboarding steps / module stages / quiz questions, cross-fade answer reveal, tab bar slides in and out. All of it switches off when the phone's "Remove animations" setting is on.
- **Not yet checked on a real phone:** the Android 13+ liquid-glass shaders. Screenshots below are rendered as Android 12L (blur tier) because the test renderer can't compile those shaders.

## Tokens

All colours live in `android/app/src/main/java/pk/parhlou/student/ui/theme/Theme.kt` (`Palette`). Roles: `primary` = main action (ink in light, lime in dark), `primaryContainer` = lime highlight, `secondary` = green icons and good states, `tertiary` = amber warnings only.

## Screens (rendered by `ScreenshotTest`, 393×852 dp, Android 12L)

`screens/` holds home, tutor (empty and in conversation), units, quiz, the paper, and Check Master with the invigilator report, in light and dark. Regenerate:

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest' -PscreenshotDir=../docs/design/screens
```
