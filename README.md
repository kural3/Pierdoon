<p align="center"><img src="docs/banner.svg" alt="Pier.doon, an offline music player for Android" width="100%"></p>

A small offline music player for Android. Jetpack Compose + Material 3 Expressive.

> **Vibe-coded.** Most of this was written by an AI assistant over a chat session and tested on a single phone. Expect bugs and rough edges. Use at your own risk.

## What it does

- Plays music from your device (tracks, albums, artists), playlists, favorites
- Library search, sorting, and selecting many tracks at once to queue, add to a playlist or delete
- Sleep timer, by minutes or at the end of the track
- Media3 playback with notification / lock screen controls, hardware audio offload
- Synced lyrics from [LRCLIB](https://lrclib.net), with an immersive lyrics mode and a per-track sync offset
- Downloads tracks by link from YouTube, SoundCloud, Bandcamp and the other sites [yt-dlp](https://github.com/yt-dlp/yt-dlp) supports, with title, artist and cover; whole playlists and albums too, or search YouTube / SoundCloud by name (Android 10+)
- Edits tags and covers, in the app or in the file; deletes tracks from the device
- Expanding player, frosted glass, wallpaper / album art / custom color themes
- A Nothing-style look (monochrome, red accent, dotted progress) and a choice of fonts, monospace included
- Remote for [Pier.doon for Windows](https://github.com/kural3/Pierdoon4Desktop) over Wi-Fi: the computer's playback in the app's own player, its library, and downloads sent to it
- Updates itself from GitHub releases
- Tries to go easy on the battery

## Install

Grab the APK from [Releases](../../releases). Android 7.0+, 64-bit (arm64) phones since 1.3; older 32-bit phones can stay on 1.2.1.

## Downloading tracks

Library → download button, or share a link to Pier.doon from any app. Paste a link, or type a name to search YouTube or SoundCloud. A playlist or album link downloads every track and makes a playlist of them in the app. Tracks are saved to `Music/Pier.doon` as M4A or Opus, exactly as the site serves them (nothing is re-encoded). The downloader updates itself once a day, so a site changing usually doesn't need a new app release.

Only download what you have the rights to.

## Build

```
./gradlew assembleRelease
```

Release builds are signed only if a `keystore.properties` file exists in the project root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); otherwise they're left unsigned.

## License

[GNU GPL v3](LICENSE). Downloads use [youtubedl-android](https://github.com/yausername/youtubedl-android) (GPL-3.0), which runs [yt-dlp](https://github.com/yt-dlp/yt-dlp) (Unlicense). Tags are written with [jaudiotagger](https://bitbucket.org/ijabz/jaudiotagger) (LGPL). Bundled fonts: [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) and [IBM Plex Mono](https://github.com/IBM/plex), both under the SIL Open Font License (texts in `app/src/main/assets/licenses`).
