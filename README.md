# StatuSave

Android app (Kotlin + Jetpack Compose) to view and download WhatsApp statuses.

## How it works

WhatsApp stores the statuses you have already viewed in a hidden folder on the phone:

- **WhatsApp**: `Android/media/com.whatsapp/WhatsApp/Media/.Statuses`
- **WhatsApp Business**: `Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses`

Since Android 11 that folder is only accessible if the user explicitly grants it through the
system folder picker (Storage Access Framework). The app opens the picker already positioned
at `.Statuses`: just tap **"Use this folder"** and **"Allow"**. The permission is persisted
(`takePersistableUriPermission`), so it is only requested once.

## Features

- **Statuses tab**: grid with the photos and videos of the statuses you have viewed, with a
  download button on each one and a full-screen preview (videos play with ExoPlayer/Media3).
- **Download to a user-defined folder**: the first time you save a status you pick the base
  folder; a subfolder (default `StatuSave`) is created automatically inside it if it doesn't
  exist. Folder and subfolder name can be changed in Settings.
- **Saved tab**: your downloaded statuses, with preview and delete. Once a status is saved it
  moves to Saved and no longer appears in the Statuses tab.
- Supports both WhatsApp and WhatsApp Business.
- No dangerous permissions in the manifest — everything goes through SAF.

## Build

Requirements: JDK 17 and the Android SDK (API 35). `local.properties` must point to the SDK,
e.g. `sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`.

```powershell
.\gradlew.bat assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

You can also open the project folder in **Android Studio** and run it on a device.

## Install on a phone

1. Enable **Developer options → USB debugging**, connect the phone and run
   `.\gradlew.bat installDebug`, **or**
2. Copy `app-debug.apk` to the phone and install it (allow "install unknown apps").

## Project structure

- `app/src/main/java/com/statusave/app/StatusRepository.kt` — SAF access to `.Statuses`,
  destination folder creation, file copy and delete.
- `app/src/main/java/com/statusave/app/MainViewModel.kt` — UI state and operations.
- `app/src/main/java/com/statusave/app/MainActivity.kt` — full Jetpack Compose UI.
- `app/src/main/java/com/statusave/app/Prefs.kt` — persisted SAF URIs and settings.
