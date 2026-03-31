# Focal App

Android MVP for offline focal animal behaviour sampling with up to three animals on one screen.

## What it does

- Starts and stops a sampling session.
- Records behaviour events locally in Room.
- Allows only one active behaviour per animal at a time.
- Auto-closes active behaviours when the session stops.
- Shows a live device clock down to the second in the top-left of the screen.
- Warns the observer at startup to check the device time against `time.is` before sampling.
- Asks at startup how many animals to monitor, from 1 to 3.
- Prompts for the selected animals' IDs when a session starts.
- Exports a session to CSV using the Android document save flow.

## Project structure

- `app/src/main/java/au/edu/cqu/focalapp/MainActivity.kt`
  Single activity entry point.
- `app/src/main/java/au/edu/cqu/focalapp/ui/FocalSamplingScreen.kt`
  Main Compose screen and export/save flow.
- `app/src/main/java/au/edu/cqu/focalapp/ui/FocalSamplingViewModel.kt`
  Session control and behaviour toggle logic.
- `app/src/main/java/au/edu/cqu/focalapp/data/local/*`
  Room entities, DAO, and database.
- `app/src/main/java/au/edu/cqu/focalapp/data/repository/FocalRepository.kt`
  Lightweight repository layer.
- `app/src/main/java/au/edu/cqu/focalapp/util/CsvExporter.kt`
  CSV generation and file writing.

## Where the main behaviour toggle logic lives

The core logic is in `FocalSamplingViewModel.onBehaviourPressed(...)`.

- If no behaviour is active, it inserts a new event with `start_time = now` and `end_time = null`.
- If the same active behaviour is pressed again, it closes that event with `end_time = now`.
- If a different behaviour is pressed, it closes the current event and immediately starts the new one using the same timestamp.

## How CSV export works

- Tap `Export CSV`.
- The app builds CSV content for the current or most recent session.
- Android's document save picker opens.
- Choose a folder/file name on the device.
- The CSV is written to that chosen location.

CSV columns:

- `session_id`
- `animal_id`
- `behaviour`
- `start_time`
- `end_time`

The exported timestamps use UTC ISO 8601 format with millisecond precision so they can be aligned with accelerometer data more easily.

## Build and run in Android Studio

1. Install Android Studio.
2. Open this folder as a project.
3. Let Android Studio install any missing SDK components.
4. If prompted, use Android SDK 35 and JDK 17.
5. Wait for Gradle sync to finish.
6. Run the app on either:
   - an Android emulator from `Device Manager`, or
   - a physical Android phone with Developer Options and USB debugging enabled.

## Build an APK for sideloading

In Android Studio:

1. Open `Build`.
2. Choose `Build Bundle(s) / APK(s)`.
3. Choose `Build APK(s)`.
4. Android Studio will show the output location when the build finishes.

The usual debug APK output path is:

`app/build/outputs/apk/debug/app-debug.apk`

You can copy that APK to an Android phone and install it directly.

## Testing on your PC

The easiest PC-based testing route is an Android emulator:

1. Open Android Studio.
2. Go to `Tools` -> `Device Manager`.
3. Create a virtual device.
4. Start the emulator.
5. Click `Run`.

If you would rather test on a real phone:

1. Enable Developer Options on the phone.
2. Enable USB debugging.
3. Plug the phone into your PC.
4. Accept the device trust/debugging prompt.
5. Run the app from Android Studio or install the generated APK.

## Note about this environment

This workspace did not have Java or Gradle available, so I could not do a local compile verification from here. The project files are complete, but the first real build should be done in Android Studio on a machine with the Android SDK installed.
