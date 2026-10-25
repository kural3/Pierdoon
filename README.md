<p align="center"><img src="docs/banner.svg" alt="Pier.doon, an offline music player" width="100%"></p>

# Pier.doon for Windows

The desktop version of [Pier.doon](https://github.com/kural3/Pierdoon): the same offline music player, the same Material 3 Expressive look, laid out for a PC window. Compose Multiplatform for Desktop, Kotlin/JVM.

> **Vibe-coded**, like the Android app. Expect rough edges.

## What it does

- Plays music from folders on your computer (Music by default, add more in Settings → Library): tracks, albums, artists, playlists, favorites, search, sorting, multi-select
- Any format FFmpeg reads (MP3, M4A/AAC, FLAC, Opus, Ogg, WAV, WMA, APE, WavPack…), gapless between tracks, output device choice, skip silence
- Desktop layout: navigation on the left, lyrics or queue in a side panel, a frosted player bar along the bottom, a two-column full player
- Synced lyrics from [LRCLIB](https://lrclib.net) with a per-track sync offset
- Downloads by link or search from YouTube, SoundCloud, Bandcamp and everything else [yt-dlp](https://github.com/yt-dlp/yt-dlp) supports (yt-dlp.exe is fetched on first use and updates itself). Drop a link on the window to download it
- Tag and cover editing, in the app or in the file; deleting sends files to the Recycle Bin
- Album art / Windows accent / custom color themes, Nothing style, fonts
- Media keys, tray icon (closing the window keeps the music playing), one instance at a time
- Updates itself from GitHub releases (the `.msi` attached to a release)

## Keyboard

| Key | Action |
| --- | --- |
| Space | Play / pause |
| ← / → | Seek 5 s |
| Ctrl+← / Ctrl+→ | Previous / next track |
| Ctrl+↑ / Ctrl+↓ | Volume |
| Ctrl+F | Search |
| Esc, Alt+←, mouse back button | Back |

Right click on a track or playlist does what a long press does on the phone.

## Build

JDK 21+ (25 is what it's built with).

```
./gradlew run                 # start it
./gradlew test                # unit tests, including real playback at volume 0
./gradlew packageReleaseMsi   # installer in build/compose/binaries/main-release/msi
```

## Where things live

- Settings, playlists, favorites, history, the saved queue, picked covers: `%APPDATA%\Pier.doon`
- Library index, lyrics cache: `%LOCALAPPDATA%\Pier.doon\cache`
- yt-dlp: `%LOCALAPPDATA%\Pier.doon\tools`
- Downloads: `Music\Pier.doon`
