<p align="center"><img src="docs/banner.svg" alt="Pier.doon, an offline music player" width="100%"></p>

# Pier.doon for Windows

The desktop version of [Pier.doon](https://github.com/kural3/Pierdoon): the same offline music player, the same Material 3 Expressive look, laid out for a PC window. Compose Multiplatform for Desktop, Kotlin/JVM.

> **Vibe-coded**, like the Android app. Expect rough edges.

## Install

Download `Pier.doon-<version>.msi` from [Releases](https://github.com/kural3/Pierdoon4Desktop/releases) and run it. It installs for the current user only (no admin rights), adds a Start menu entry and a desktop shortcut, and later updates itself from the same Releases page. Windows 10/11 x64.

## What it does

**Library**
- Plays music from folders on your computer (Music by default; add more, or leave subfolders out, in Settings → Library): tracks, albums, artists, playlists, favorites, search, sorting, multi-select
- Tag and cover editing, in the app or in the file; deleting sends files to the Recycle Bin
- Downloads by link or search from YouTube, SoundCloud, Bandcamp and everything else [yt-dlp](https://github.com/yt-dlp/yt-dlp) supports, into Music/Pier.doon or a folder of your choice (yt-dlp.exe is fetched on first use, checked against its published SHA-256, and updates itself). Drop a link on the window to download it

**Playback**
- Any format FFmpeg reads (MP3, M4A/AAC, FLAC, Opus, Ogg, WAV, WMA, APE, WavPack…), gapless between tracks, output device choice, skip silence
- Synced lyrics from [LRCLIB](https://lrclib.net) with a per-track sync offset
- Sleep timer, media keys and the Windows media overlay (title, cover, controls), tray icon (closing the window keeps the music playing), one instance at a time
- Discord status ("Listening to Pier.doon": track, artist, time bar, cover), switched on in Settings → Интеграции
- Remote control from the Pier.doon Android app over Wi-Fi: player, queue, lyrics, covers, library (the Телефон tab in the side menu; protocol in [docs/remote-api.md](docs/remote-api.md))

**Desktop layout**
- Navigation on the left: drag its right edge to resize it, down to a column of round icons; double-click the edge to switch
- A frosted player bar along the bottom; pull it up or click it and it unfolds into the full player
- Two-column full player: art and controls on the left, lyrics or the queue on the right. With lyrics showing, after a few idle seconds the controls fold into a compact bar and the art grows, as on the phone
- Lyrics or queue also open in a side panel next to the library
- Right-click menus on tracks and playlists
- Album art / Windows accent / custom color themes, Nothing style, fonts, frosted glass on or off

## Keyboard

| Key | Action |
| --- | --- |
| Space | Play / pause |
| ← / → | Seek 5 s |
| Ctrl+← / Ctrl+→ | Previous / next track |
| Ctrl+↑ / Ctrl+↓ | Volume |
| Ctrl+F | Search |
| Esc, Alt+←, mouse back button | Back |

## Build

Any JDK to start Gradle with; the JDK 25 it builds with is downloaded automatically if missing.

```
./gradlew run                 # start it
./gradlew test                # unit tests, including real playback at volume 0
./gradlew packageReleaseMsi   # installer in build/compose/binaries/main-release/msi
```

`./gradlew shots -PshotsScript=<file>` renders the app off screen from a script of steps (clicks, drags, waits, screenshots, frame timings) on a separate copy of the app data; see `src/test/kotlin/com/ku/pierdoon/Shots.kt`.

## Where things live

- Settings, playlists, favorites, history, the saved queue, picked covers: `%APPDATA%\Pier.doon`
- Error log (attach it to a bug report): `%APPDATA%\Pier.doon\logs\errors.log`
- Library index, lyrics cache: `%LOCALAPPDATA%\Pier.doon\cache`
- yt-dlp: `%LOCALAPPDATA%\Pier.doon\tools`
- Downloads: `Music\Pier.doon`
