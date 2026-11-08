# Nudge — Medicine Reminder (Android) Plan

## Context
User struggles to track meds tied to meal timing (before/after food × morning/noon/night); confusion
leads to skipped doses. Goal: an Android app that answers "what do I take right now?" with one tap
to confirm, and reminders that keep nagging until acted on.

**Current step (this session): planning only.** On approval, save this plan as `PLAN.md` in
`/Users/mohitsingh/myFiles/medicine reminder/` (empty folder today), `git init`, and stop.
Implementation happens later; the project will be pushed to GitHub and built on the user's other PC
(which has Android Studio). No build tooling is needed on this Mac.

## Core idea: organise by meal slot, not clock time
6 slots = {Morning, Noon, Night} × {Before food, After food}.

User's meal times (seed defaults, editable in Settings):
| Meal | Time | Before-food reminder (−30 min) | After-food reminder (+15 min) |
|---|---|---|---|
| Breakfast | 09:00 | 08:30 | 09:15 |
| Lunch | 12:30 | 12:00 | 12:45 |
| Dinner | 20:00 | 19:30 | 20:15 |

The offsets (30 min before, 15 min after) are assumed defaults the user didn't specify; they're editable in Settings.

## Features (v1)
1. **Add/Edit medicine**: name, dose (e.g. 1 tablet / 5 ml), colour tag or pill photo, slots
   (3×2 grid picker, multi-select), start date, duration (N days or ongoing), notes.
2. **Today screen**: hero card for the current/next slot ("NOW: Before breakfast · 2 medicines")
   with ONE big **"Taken all"** button (requested). Per-medicine ✓/Skip still available by expanding.
   Below: timeline of remaining slots; completed slots collapsed.
3. **"I just ate" button** (requested) on Today screen and in the before-food notification:
   - Tapped → after-food reminder rescheduled to *now + after-offset*.
   - Not tapped → after-food reminder still fires at scheduled time (meal + offset) as fallback.
4. **Reminders**: exact-time notification per slot with actions **Taken all** / **Snooze 10 min**
   / **I just ate** (before-food only). Re-notify every 10 min until Taken/Skip, give up after
   ~1 hour and mark slot "Missed". Rescheduled on reboot / time change.
5. **Home-screen widget** (requested): shows next slot, its medicines, and a "Taken" button.
6. **History**: month calendar (green = all taken, amber = partial, red = missed) + missed list.
7. **Settings**: meal times, before/after offsets, re-notify interval, max re-notify duration.

Deferred (not v1): refill tracker, caregiver alert, language/large-text mode, PDF export.

## Screen layout
- Bottom nav: **Today | Medicines | History | Settings**
- Medicines: list grouped by slot, FAB "+ Add".

## Tech
- Kotlin, Jetpack Compose, Material 3, MVVM, Hilt
- Room DB — tables: `Medicine`, `MedicineSlot` (medicineId, slot), `DoseLog` (date, slot,
  medicineId, status TAKEN/SKIPPED/MISSED, time), `MealSettings` via DataStore
- Alarms: `AlarmManager.setExactAndAllowWhileIdle` + `BroadcastReceiver`; receivers for
  `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` to reschedule
- Notification action receiver handles Taken all / Snooze / I just ate without opening the app
- Widget: Jetpack Glance
- Permissions: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`,
  `RECEIVE_BOOT_COMPLETED`; prompt to disable battery optimisation (important on Xiaomi/Oppo/Vivo etc.)
- minSdk 26, targetSdk latest

## Verification (later, on Android Studio PC)
- `./gradlew installDebug` on device/emulator
- Set a meal time 2 min ahead → before-food notification fires; tap "I just ate" → after-food
  fires at now+15; without tapping → fires at scheduled time
- Ignore a notification → re-notifies every 10 min, marked Missed after 1 h
- "Taken all" from notification, app and widget all log doses; History updates
- Reboot device → alarms still fire

## Branding
- **App name:** Nudge (`applicationId` e.g. `com.mohit.nudge` — confirm before first build)
- **Icon:** tilted two-tone capsule with 2–3 short motion lines, as if gently pushed.
  Flat, no text, artwork within central ~66% safe zone (adaptive icon).
- **Colours:** primary teal `#00897B`, accent coral `#FF7043` (capsule half), light background.
- Deliver as adaptive icon vector drawables (`ic_launcher_foreground.xml` / `_background.xml`)
  plus a monochrome layer for Android 13+ themed icons.
