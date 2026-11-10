# Reelback

Reelback is an offline video gallery for Android 11 and later. The feed reads the videos Android indexes in MediaStore. The app has no network permission, account, analytics, or video copies.

- On first launch, choose which albums appear in Home. Suggested Camera albums start with portrait clips only. Folders named for common download apps are suggested too. Use the Orientation control to expand an album to all orientations. Edit your choices from Settings → Feed albums or the filter icon on Albums.
- Home shuffles eligible clips by default. After 80% of a clip has played, it appears in History and is skipped on future visits to Home. Seeking does not count as playback. The current clip stays on screen until you leave it. You can still play a watched clip from History, Favorites, or its album.
- Landscape videos are shown in full in the vertical player.
- Swipe up and down to browse. Tap to pause and reveal the timeline, or drag horizontally along the bottom edge to scrub while playing. Double tap to save a favorite.
- Hold the middle of the video for Clear display, speed, mute, and other actions. Hold either side while playing for temporary 2× speed. Tap the exit icon or press Back to leave Clear display.
- Favorites are stored in Reelback. Albums show the folders Android reports for your gallery videos. Tap the album cover or name on a playing video to open that album, and press Back to return to the same clip. The album’s newest available video supplies its cover. Tap a tile to open a feed limited to that collection.
- Share uses Android’s share sheet. Trash uses Android’s confirmation and moves the original video to the system Trash. The left swipe shortcut is off by default and uses the same confirmation when enabled.
- Settings offers feed order, fit, the Trash gesture, and video access. Android 14 and later can grant access to a selected set of videos.

## Build

Open this folder in Android Studio with JDK 17, Android SDK Platform 36, and Android SDK Build Tools 36.0.0. The Gradle project uses Android Gradle Plugin 8.13.2 and has no runtime dependencies.

You can also build and sign directly on Linux or macOS with the official SDK tools and JDK 17. Set `JAVA_HOME` and `ANDROID_SDK_ROOT`. To create your own signing key, run:

```sh
keytool -genkeypair -keystore reelback.p12 -storetype PKCS12 -alias reelback \
  -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Reelback"
```

Set `REELBACK_KEYSTORE` to the keystore’s absolute path and `REELBACK_STORE_PASSWORD` to its password in your shell environment. Then run `./build-apk.sh` to produce `build/Reelback.apk`. An output path can be passed as the first argument. The signing key is separate from the source. Android requires the same signing key to install an update over an existing installation.

## Playback and storage

The app uses one native Android media player at a time, loads thumbnails off the main thread, and caches nearby thumbnails for swipes. Playback uses the device’s supported formats and codecs. Favorites, watch history, and feed preferences live in app storage; uninstalling the app clears them.
