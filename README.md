# Anki Streak 🔥

A tiny companion app that gives AnkiDroid a Duolingo-style streak.
It doesn't change AnkiDroid at all. It only reads your deck counts through AnkiDroid's official API.

## How it works

- Every ~15 minutes (and whenever you open it), Anki Streak asks AnkiDroid how many cards are left today.
- When **0 cards are left** (the "Congratulations! You have finished…" moment), today counts and your streak goes up.
- The day starts at **04:00** by default, the same as Anki, so finishing at 01:00 still counts for "yesterday".
  If you changed Anki's "next day starts at" setting, change it here too.
- If today isn't done yet, your streak stays alive until the day ends. Miss a whole day and it resets to 0.

## Features

- Big flame + streak counter, the last 7 days, and your longest streak
- 🔥 notification when your streak goes up
- Evening reminder (default 20:00) that only appears if you haven't finished yet; tapping it opens AnkiDroid
- Home-screen widget (orange flame = done, grey = cards waiting)
- Choose which decks count (default: all decks)
- "Ignore new cards" option: only reviews and learning cards have to be finished
- Long-press a day to fix it by hand (for example, if your phone was off)

## Install

1. Make sure AnkiDroid is already installed (install AnkiDroid **before** this app).
2. Install `AnkiStreak.apk` (allow "install unknown apps" for your browser or file manager when asked).
3. Open Anki Streak and tap **Allow access**.
4. If it says it can't read AnkiDroid: open AnkiDroid once, and check that
   **Settings → Advanced → Enable AnkiDroid API** is on.
5. Optional: long-press your home screen → Widgets → Anki Streak.

Tip: if your phone kills background apps aggressively (Xiaomi, Samsung, Huawei…), set Anki Streak's
battery usage to "Unrestricted" so the background check keeps running.

## Build it yourself

**Option A: GitHub (no computer setup needed)**
Push this folder to a GitHub repo. The included workflow (`.github/workflows/build.yml`) builds the APK
and attaches it to a new release. Open the repo's **Releases** page on your phone and download `AnkiStreak.apk`.

**Option B: Android Studio**
Open this folder in Android Studio and press Run, or run `./gradlew assembleRelease`.
The APK ends up in `app/build/outputs/apk/release/`.

The signing key (`app/streak.keystore`) is committed on purpose, so every new build installs as an update and
keeps your streak. That's fine for a personal app. Don't reuse this key for anything you publish.

## Known limits

- Android only allows background checks about every 15 minutes, so the 🔥 notification can come a few minutes
  after you finish. Opening the app updates it right away.
- AnkiDroid's API doesn't share your review history, so the app only sees "cards left today". On a day where
  your chosen decks have nothing due at all, that day counts automatically.
# anki_streak
