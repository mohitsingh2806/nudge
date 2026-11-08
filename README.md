# Nudge — Medicine Reminder (Android)

Reminders built around **meals**, not clock times. Each medicine goes in one or more of 6 slots
(Morning / Noon / Night × Before / After food). Change your breakfast time once and every morning
reminder moves with it.

## Build & run (on the PC with Android Studio)
1. Clone the repo, then **File → Open** the project folder in Android Studio (Ladybug 2024.2+ or newer).
2. Let Gradle sync (uses Gradle 8.9 from `gradle/wrapper/gradle-wrapper.properties`).
   - This repo has no `gradlew` scripts / wrapper jar yet. Android Studio works without them; to add
     them for command-line builds, run `gradle wrapper --gradle-version 8.9` once and commit the result.
3. Plug in your phone (USB debugging on) and press **Run ▶**.
4. On first launch allow notifications, then open **Settings → Make reminders reliable** and fix
   anything shown in red (especially battery optimisation on Xiaomi / Oppo / Vivo / Realme / Samsung).

Unit tests: `./gradlew test` (once the wrapper is added) or right-click `app/src/test` → Run tests.

## Features
- **Today**: big "NOW / NEXT" card with one **Taken all** button, plus the full day's timeline with
  per-medicine ✓ / ✕ (tap a status chip to undo).
- **I just ate**: moves the after-food reminder to *now + offset*. If you never tap it, the after-food
  reminder still fires at the planned meal time + offset.
- **Reminders**: notification with *Taken all / Snooze / I just ate*. Repeats every 10 min until you act;
  after 60 min the doses are marked **Missed**. Survives reboot, app update and clock changes.
- **Home-screen widget**: next slot, its medicines and a Taken all button.
- **History**: monthly calendar (green / amber / red) and per-day details.
- **Settings**: meal times (default 9:00 / 12:30 / 20:00), before (30 min) / after (15 min) offsets,
  repeat interval, give-up time.

## Code map
```
app/src/main/java/com/mohit/nudge/
  NudgeApp.kt            Application + AppContainer (manual DI)
  MainActivity.kt
  data/                  Room entities & DAOs, DataStore settings, NudgeRepository
  domain/                SlotTimes (when each slot fires), TodayState, History
  reminders/             ReminderScheduler (AlarmManager), Notifier, AlarmReceiver, BootReceiver
  widget/                Glance home-screen widget
  ui/                    Compose screens: today, medicines (list + edit), history, settings
```

Note: the plan mentioned Hilt; a small manual `AppContainer` is used instead. It's simpler for an
app this size and easier to use from broadcast receivers and the widget.

See [PLAN.md](PLAN.md) for the original plan.

## Build online (no Android Studio needed)
Every push to GitHub runs `.github/workflows/build.yml`, which runs the unit tests and builds a debug APK.
1. Push the repo to GitHub.
2. Open the repo's **Actions** tab, then the latest **Build APK** run.
3. Download **nudge-debug-apk** from the run's Artifacts section and unzip it.
4. Copy the `.apk` to your phone and open it (allow "Install unknown apps" when asked).
If the build fails, the run log shows the exact compile error.
