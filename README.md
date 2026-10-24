# Pier.doon

A small offline music player for Android. Jetpack Compose + Material 3 Expressive.

> ⚠️ **Vibe-coded.** Most of this was written by an AI assistant over a chat session and tested on a single phone. Expect bugs and rough edges. Use at your own risk.

## What it does

- Plays music from your device (tracks, albums, artists), playlists, favorites
- Media3 playback with notification / lock screen controls, hardware audio offload
- Synced lyrics from [LRCLIB](https://lrclib.net), with an immersive lyrics mode
- Expanding player, frosted glass, wallpaper / album art / custom color themes
- Tries to go easy on the battery

## Install

Grab the APK from [Releases](../../releases). Android 7.0+.

## Build

```
./gradlew assembleRelease
```

Release builds are signed only if a `keystore.properties` file exists in the project root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); otherwise they're left unsigned.
