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

## Tokens

All colours live in `android/app/src/main/java/pk/parhlou/student/ui/theme/Theme.kt` (`Palette`). Roles: `primary` = main action (ink in light, lime in dark), `primaryContainer` = lime highlight, `secondary` = green icons and good states, `tertiary` = amber warnings only.

## Screens (rendered by `ScreenshotTest`, 393×852 dp)

`screens/` holds home, tutor, profile, units and quiz in light and dark. Regenerate:

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest' -PscreenshotDir=../docs/design/screens
```
