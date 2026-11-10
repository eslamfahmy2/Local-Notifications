# Local Notifications

An Android app that downloads a list of notifications from an XML feed
(`http://lynxapp.com/localalerts.php`) and lets you schedule each one as a local notification.

Built with **Kotlin, Clean Architecture, MVI, Hilt, WorkManager** and a multi-module setup.

## What the app does

| Screen | What you can do |
|---|---|
| **List** | See the notifications from the feed (with a progress bar while loading). **Cancel All** pending notifications. The list reloads when the app comes back from the background (e.g. after Home), but not on rotation or when returning from the detail screen. |
| **Detail** | See the title. **Schedule this notification**: a dialog asks you to confirm first, then (Android 13+) the permission is asked and a dialog confirms it was scheduled. **Cancel** it (only enabled while it is scheduled). |
| **Notification** | Fires after its delay. Tapping it opens the app. |

Portrait and landscape, Material 3, edge to edge.

## Two apps, same core

| Module | Role |
|---|---|
| `:app-views` | **The real app.** Views + RecyclerView. Build and ship this one. |
| `:app-compose` | **Showcase.** The same screens in Jetpack Compose, on the same ViewModels. Debug only, installs next to the real app (`.compose` suffix). |

## Structure

```
app-views/            Views UI + Hilt wiring  →  final APK
app-compose/          Compose UI + Hilt wiring (showcase)
feature/list/         List screen logic: ListViewModel + contract (no UI)
feature/detail/       Detail screen logic: DetailViewModel + contract (no UI)
core/domain/          Pure Kotlin: models, errors, interfaces, use cases
core/data/            Retrofit + SAX XML parser + repository
core/notifications/   WorkManager scheduler, worker, notification + permission helpers
core/mvi/             Small MVI base ViewModel (state, intents, one-time effects)
core/testing/         Test fakes (repository, scheduler) shared by all tests
core/device-testing/  Emulator test runner + helpers shared by both apps
```

### How the modules depend on each other

```
   app-views (real app)                   app-compose (showcase)
          |      :                               :      |
          +------:-------------+-----------------:------+
                 :             |                 :
                 :             v                 :
                 :   +-------------------+       :
                 :   |  feature:list     |       :
                 :   |  feature:detail   |       :
                 :   +-------------------+       :
                 :        |         |            :
                 :        v         v            :
                 :   core:mvi   core:domain      :
                 :                ^     ^        :
                 :                |     |        :
                 :     core:data -+     +- core:notifications
                 :         ^                  ^  :
                 :.........:..................:..:

   ----->  depends on
   ......  Hilt wiring: each app plugs the real core:data and core:notifications
           into the core:domain interfaces
```

Everything points **inward to `core:domain`**. Screens never see Retrofit or WorkManager; the apps
connect the interfaces to their implementations with Hilt.

### How data flows (MVI)

```
   +--------+   intent ("Schedule clicked")   +-----------+
   | Screen | ------------------------------> | ViewModel |
   |        | <------------------------------ |           |
   +--------+   state + effect (e.g. dialog)  +-----------+
                                                    |
                                                    v
                                              +----------+
                                              | Use case |
                                              +----------+
                                                |      |
                                  .-------------'      '-------------.
                                  v                                  v
                             Repository                          Scheduler
                                  |                                  |
                                  v                                  v
                              XML feed                          WorkManager
                          (Retrofit + SAX)                           |
                                                                     v
                                                               Notification
```

The screen sends **intents**, the ViewModel keeps one **state** for the screen to draw, and sends
**effects** for things that happen once (dialog, snackbar, navigation).

### Scheduling a notification, step by step

```
 tap "Schedule"
      |
      v
 "Schedule this notification?" ---- Cancel ---> nothing is scheduled
      | Schedule
      v
 Android 13+ and no permission? ---- yes ---> system asks the user (allow or deny)
      | no                                                |
      v                                                   |
 DetailViewModel -> ScheduleNotificationUseCase <---------'   (scheduled either way)
      |
      v
 WorkManager waits the item's delay  . . . . .  "Cancel" removes it before it fires
      |
      v
 NotificationWorker posts the notification
      |
      v
 user taps it ---> app opens
```

## Key decisions

- **WorkManager** for scheduling (no exact-alarm permission). It can fire a few seconds late.
  It is behind the `NotificationScheduler` interface, so switching to AlarmManager is one new class.
- **Plain HTTP** is allowed only for `lynxapp.com` (network security config).
- **Confirm before scheduling:** tapping Schedule only asks for confirmation; `DetailViewModel`
  schedules on `ScheduleConfirmed`, so both apps follow the same rule.
- **Android 13+:** the notification permission is asked after you confirm. If denied, it still
  schedules and the dialog explains how to turn notifications on.
- **List reload:** only when the whole app comes back from the background (tracked with
  `ProcessLifecycleOwner`), not on rotation or when returning from the detail screen.
- **Bad feed items are skipped**, so one broken entry does not hide the rest.
- minSdk 26, target/compile SDK 36, Java 17.

## Build

```
./gradlew :app-views:assembleDebug       # the real app (Views)
./gradlew :app-compose:assembleDebug     # the Compose showcase app
```


## Tests

Two kinds:

- **JVM tests** (`src/test`): run on your computer in seconds, no emulator. UI tests here use
  **Robolectric**, which simulates Android.
- **Emulator tests** (`src/androidTest`): run on a real emulator or phone. Only for what needs real
  Android: real rotation, the system permission dialog, notifications in the shade.

| Module | JVM tests | Emulator tests | What is covered |
|---|---:|---:|---|
| `core:domain` | 11 | | Models, use cases |
| `core:data` | 25 | | Retrofit call, XML parser, mapper, repository (fake server) |
| `core:notifications` | 25 | | Scheduler, worker, notification, permission, schedule → notification flow |
| `core:mvi` | 6 | | State and effect delivery |
| `feature:list` | 12 | | List ViewModel |
| `feature:detail` | 13 | | Detail ViewModel, including the confirm step |
| `app-views` | 31 | 10 | Both screens, full app flow with a fake server; on device: rotation, background reload, confirmation dialog, permission dialog, real notification |
| `app-compose` | 29 | 7 | Both Compose screens and routes; on device: rotation, background reload, confirmation dialog, permission dialog, landscape |

### Run them

```
./gradlew test                                     # all JVM tests (fast, no emulator)

./gradlew :app-views:testDebugUnitTest             # only the Views app JVM tests
./gradlew :app-compose:testDebugUnitTest           # only the Compose app JVM tests
```

Emulator tests (start an emulator first):

```
./gradlew :app-views:connectedDebugAndroidTest
./gradlew :app-compose:connectedDebugAndroidTest
```