# Daily Tracker — Phone-only GitHub APK Build

This repository is prepared so GitHub Actions can build the Android APK in the cloud.

## From your phone

1. Create a new GitHub repository, e.g. `daily-tracker`.
2. Upload ALL files and folders from this project to the repository.
   Important: keep `.github/workflows/build-apk.yml` in exactly that path.
3. Open the repository's **Actions** tab.
4. Open **Build Android APK**.
5. If no build is running, tap **Run workflow**.
6. Wait for the build to finish with a green check.
7. Open the completed workflow run.
8. Scroll to **Artifacts**.
9. Download **Daily-Tracker-APK**.
10. Extract the downloaded ZIP and install `app-debug.apk` on your Android phone.

Android may ask you to allow installing apps from your browser/files app.

## Current limitation

The APK contains the current tracker and simulated Watch/Health test mode.
Real Health Connect integration is the next native step.

## App ID

`com.mrcdrnzz.dailytracker`
