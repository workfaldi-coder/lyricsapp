SPOTIFY & YOUTUBE TRANSPARENT OVERLAY FOR ANDROID (NO API KEYS NEEDED)
======================================================================

FEATURES:
- Transparent always-on-top floating lyrics & subtitle overlay (Android 8.0 - 15).
- Spotify & YouTube Music synced lyrics with exact playback timestamp (works even
  when Spotify / YT Music is minimized or in the background!).
- Live YouTube app CC subtitles via Android Accessibility Service (works in
  normal mode, split-screen, and Picture-in-Picture).
- Quick notification toggle for Click-Through Mode (lock/unlock dragging) & Stop.

HOW TO BUILD THE .APK FILE
--------------------------

OPTION 1: FREE CLOUD BUILD VIA GITHUB (NO ANDROID STUDIO NEEDED - ~2 MINUTES)
1. Create a new empty repository on GitHub (public or private).
2. Upload all files inside this unzipped folder (including the hidden
   ".github/workflows/build-apk.yml" folder) to the repository.
3. Click the "Actions" tab on GitHub -> wait ~2 minutes for "Build Android APK"
   to finish -> download "SpotifyYouTubeOverlay-Android-APK".
4. Unzip it to get "app-debug.apk", transfer it to your Android phone, and tap
   to install (enable "Install unknown apps" when prompted).

OPTION 2: BUILD LOCALLY IN ANDROID STUDIO (MACBOOK AIR M3 OR WINDOWS)
1. Unzip this folder and open it in Android Studio (File -> Open).
2. Wait for Gradle Sync to finish, then click Build -> Build Bundle(s) / APK(s)
   -> Build APK(s).
3. Your APK is saved at: app/build/outputs/apk/debug/app-debug.apk

FIRST-TIME PHONE SETUP (30 SECONDS)
-----------------------------------
1. Open "Spotify & YouTube Overlay" on your phone.
2. Tap "Open" next to "Display Over Other Apps" -> allow it.
3. Tap "Open" next to "Notification Access" -> enable "Lyrics Overlay Media Reader"
   (this lets the app read Spotify & YouTube Music song + exact timestamp).
4. Optional for YouTube subtitles: Tap "Open" next to "Accessibility Service" ->
   Installed apps -> enable "YouTube Live Subtitle Reader".
   (Note: On Android 13+, if Accessibility is greyed out as "Restricted setting",
   go to Phone Settings -> Apps -> Spotify & YouTube Overlay -> tap the top-right
   three-dot menu -> "Allow restricted settings", then turn Accessibility on.)
5. Tap "Start Floating Overlay"!
