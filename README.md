<p align="center"><img src="docs/banner.svg" alt="Pier.doon, an offline music player" width="100%"></p>

A small offline music player for Android and Windows, with the same Material 3 Expressive look on both. Jetpack Compose on the phone, Compose Multiplatform for Desktop on the PC.

> **Vibe-coded.** Most of this was written by an AI assistant over a chat session and tested on one phone and one PC. Expect bugs and rough edges. Use at your own risk.

## Install

Both apps are on the [latest release](../../releases/latest), and both then update themselves from it.

- **Android**: `Pierdoon-<version>.apk`. Android 7.0+, 64-bit (arm64).
- **Windows**: `Pier.doon-<version>.msi`. Windows 10/11 x64. Installs for the current user only (no admin rights), with a Start menu entry and a desktop shortcut.

What changed in each version is in its release notes.

## What it does

**On both**
- Plays your music: tracks, albums, artists, playlists, favorites, search, sorting, selecting many tracks at once to queue, add to a playlist or delete
- Synced lyrics from [LRCLIB](https://lrclib.net), with a per-track sync offset
- Downloads by link or by search from YouTube, SoundCloud, Bandcamp and the other sites [yt-dlp](https://github.com/yt-dlp/yt-dlp) supports, with title, artist and cover; whole playlists and albums too
- Edits tags and covers, in the app or in the file
- Sleep timer, by minutes or at the end of the track
- Expanding player, frosted glass, album art / wallpaper or Windows accent / custom color themes, a Nothing-style look (monochrome, red accent, dotted progress) and a choice of fonts
- The phone works as a remote for the computer over Wi-Fi: the computer's playback in the phone's own player, its queue, lyrics and library, and downloads sent to it

**Android**
- Media3 playback with notification and lock screen controls, hardware audio offload
- Immersive lyrics mode, landscape player
- Share a link to Pier.doon from any app to download it (Android 10+)
- Tries to go easy on the battery

**Windows**
- Plays music from folders on your computer (Music by default; add more, or leave subfolders out, in Settings → Library). Deleting sends files to the Recycle Bin
- Any format FFmpeg reads (MP3, M4A/AAC, FLAC, Opus, Ogg, WAV, WMA, APE, WavPack…), gapless between tracks, output device choice, skip silence
- Media keys and the Windows media overlay, tray icon (closing the window keeps the music playing), one instance at a time
- Discord status ("Listening to Pier.doon"), switched on in Settings → Интеграции
- Desktop layout: resizable navigation on the left, a player bar along the bottom that unfolds into a two-column full player, lyrics or queue in a side panel, right-click menus. Drop a link on the window to download it

## Downloading tracks

Paste a link, or type a name to search YouTube or SoundCloud. A playlist or album link downloads every track and makes a playlist of them in the app. Tracks go to `Music/Pier.doon` (on Windows, any folder you choose) as M4A or Opus, exactly as the site serves them, nothing re-encoded. The downloader updates itself, so a site changing usually doesn't need a new app release. On Windows, yt-dlp.exe is fetched on first use and checked against its published SHA-256.

Only download what you have the rights to.

## Keyboard (Windows)

| Key | Action |
| --- | --- |
| Space | Play / pause |
| ← / → | Seek 5 s |
| Ctrl+← / Ctrl+→ | Previous / next track |
| Ctrl+↑ / Ctrl+↓ | Volume |
| Ctrl+F | Search |
| Esc, Alt+←, mouse back button | Back |

## Where things live on Windows

- Settings, playlists, favorites, history, the saved queue, picked covers: `%APPDATA%\Pier.doon`
- Error log (attach it to a bug report): `%APPDATA%\Pier.doon\logs\errors.log`
- Library index, lyrics cache: `%LOCALAPPDATA%\Pier.doon Data\cache`
- yt-dlp: `%LOCALAPPDATA%\Pier.doon Data\tools`
- The app itself: `%LOCALAPPDATA%\Pier.doon`. Every upgrade and uninstall wipes this folder, so the app keeps nothing else there.

## Build

One Gradle build for both apps. Any JDK starts Gradle; the JDK 25 it builds with is downloaded if missing.

| folder | what |
| --- | --- |
| `app/` | the Android app |
| `desktop/` | the Windows app |
| `core/` | code both use: the track model, library lists and search, lyrics parsing, track naming, queue restore, version compare. Plain Kotlin/JVM, nothing platform-specific |
| `ui/` | Compose code both draw with: expressive shapes and buttons, the colour scheme generator, transitions, the colour picker, the expanding player, the icons. Kotlin Multiplatform (Android + JVM) |
| `docs/` | [the phone ↔ computer protocol](docs/remote-api.md) |

```
./gradlew :app:assembleRelease          # APK
./gradlew :desktop:run                  # start the Windows app
./gradlew :desktop:packageReleaseMsi    # installer, in desktop/build/compose/binaries/main-release/msi
./gradlew :core:test :desktop:test :app:testDebugUnitTest :app:lintDebug
```

Release builds of the APK are signed only if a `keystore.properties` file exists in the project root or in `Pierdoon-signing/` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); otherwise they're left unsigned.

`./gradlew :desktop:shots -PshotsScript=<file>` renders the Windows app off screen from a script of steps (clicks, drags, waits, screenshots, frame timings) on a separate copy of the app data; see `desktop/src/test/kotlin/com/ku/pierdoon/Shots.kt`.

## Releasing

One release for both apps: tag `v<version>`, the APK and the MSI attached, marked as the latest release, notes in Russian.

```
gh release create v<version> Pierdoon-<version>.apk Pier.doon-<version>.msi --title "Pier.doon <version>" --notes-file notes.md --latest
```

- Each app's updater takes the newest release that has its own file (`.apk` or `.msi`), so a release for just one app works too.
- Older Android versions only look at the release marked latest, so a release with an APK must be the latest.
- The MSI needs a three-part version (`x.y.0`); the updater treats `x.y` and `x.y.0` as the same.
- Never keep files in the Windows install folder: the installer wipes it on every upgrade.

## License

[GNU GPL v3](LICENSE). Downloads use [yt-dlp](https://github.com/yt-dlp/yt-dlp) (Unlicense), on Android through [youtubedl-android](https://github.com/yausername/youtubedl-android) (GPL-3.0). Tags are written with [jaudiotagger](https://bitbucket.org/ijabz/jaudiotagger) (LGPL). Windows playback uses FFmpeg through [JavaCV](https://github.com/bytedeco/javacv). Bundled fonts: [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) and [IBM Plex Mono](https://github.com/IBM/plex), both under the SIL Open Font License (texts in `app/src/main/assets/licenses`).
