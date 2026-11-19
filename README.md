# ScaleLock

An Android utility app that blocks distracting apps each morning (**05:00 – 11:00**) until a weigh-in is recorded in **Health Connect** (e.g. synced from a Withings smart scale).

## Why?
Built to enforce a morning routine—getting out of bed, stepping on a smart scale, and actively logging daily weight—without relying on cloud webhooks, third-party automation bridges, or battery-draining background polling.

## How It Works
- **Event-driven app blocking:** An Android `AccessibilityService` (`AppBlockerService`) listens for `TYPE_WINDOW_STATE_CHANGED` events and intercepts launches of user-selected distracting apps during the active morning window.
- **Foreground Health Connect verification:** When a blocked app is opened, `LockActivity` launches in the foreground and queries `HealthConnectClient` for a `WeightRecord` recorded since `04:00` today. Because the check runs in a foreground activity, it avoids background health-data polling and requires zero background battery overhead.
- **Daily unlock caching:** Once today's weigh-in is verified, the app caches the unlocked date locally so subsequent app launches pass through immediately for the rest of the day.

## Safety & Fail-Safes
- **Hard time cutoff:** Blocking only applies between `05:00` and `11:00` and automatically stops at `11:00` even if no weigh-in occurred.
- **Protected apps:** Phone/Dialer, Messages, Clock, Withings, Health Connect, System UI, Launcher, and Keyboard packages are permanently excluded from blocking.
- **OS-level fallback:** Booting Android into Safe Mode disables third-party Accessibility Services if an emergency override is ever needed.

## Tech Stack
- **Language:** Kotlin
- **Android APIs:** `AccessibilityService`, **Health Connect** (`androidx.health.connect:connect-client`), Material 3 (`ViewBinding`, `RecyclerView`, Kotlin Coroutines)
- **Min / Target SDK:** Android 9 (API 28) / Android 15 (API 35)

## Build & Install
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After installing on Android 13+:
1. Grant **Health Connect** weight read permission inside the app.
2. Enable **ScaleLock Blocker** in **Settings → Accessibility** *(if grayed out as a restricted setting on a sideloaded APK, open **Settings → Apps → ScaleLock → ⋮ → Allow restricted settings** first)*.
