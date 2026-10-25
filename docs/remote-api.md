# Phone connection API

Pier.doon for Windows can be driven from the Pier.doon Android app over the local network. This is the protocol, version 1 (`api: 1`). The server is in `src/main/kotlin/com/ku/pierdoon/remote/`.

The server is off by default. The user turns it on in Settings → Телефон и Discord → Управление с телефона. While it runs, that page shows a six-digit pairing code and the computer's addresses.

## Finding the computer

Send the UDP datagram `PIERDOON_DISCOVER` (UTF-8, nothing else) to port **47232**, to the broadcast address (`255.255.255.255` or the subnet's). Every running Pier.doon replies to the sender:

```json
{"app": "Pier.doon", "name": "DESKTOP-7O84MEM", "port": 47231, "version": "1.0.4", "api": 1}
```

Use the packet's source address with `port`. If nothing replies within a couple of seconds, offer manual entry of `address:47231`.

## HTTP

- Base URL: `http://<address>:47231/api`. The traffic is plain HTTP, so on Android 9+ the app needs `usesCleartextTraffic` or a network security config.
- Bodies are JSON in UTF-8.
- Send `Accept-Encoding: gzip`: large responses (the library) come back compressed.
- Every endpoint except `/info` and `/pair` needs `Authorization: Bearer <token>`. Image loaders that can't set headers may add `?token=<token>` to the URL instead.
- `401` means the token is unknown: the phone was unpaired on the PC, so pair again.

### `GET /info` (no auth)

```json
{"app": "Pier.doon", "name": "DESKTOP-7O84MEM", "version": "1.0.4", "api": 1}
```

### `POST /pair` (no auth)

```json
{"code": "123456", "name": "Galaxy S25+"}
```

- `200 {"token": "…64 hex…", "name": "DESKTOP-7O84MEM"}`. Keep the token; it doesn't expire. The PC shows a new code after each successful pairing.
- `403` wrong code. After five wrong codes the PC switches to a new code.

### `POST /unpair`

Forgets this phone's token on the PC.

### `GET /events`

A [server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html) stream that stays open. Right after connecting it sends `state`, `queue` and `library`, then every change as it happens. A `: keepalive` comment comes every 15 s; if nothing arrives for ~45 s, reconnect.

| event | data | when |
| --- | --- | --- |
| `state` | [State](#state) | track, play/pause, seek, shuffle, repeat, volume |
| `queue` | [Queue](#queue) | the queue changed |
| `library` | `{"version": n}` | tracks, playlists or favourites changed: fetch `/library` again |
| `lyricsOffset` | `{"songId": …, "offsetMs": …}` | the sync offset of the current track changed |

### `GET /state` → State

```json
{
  "song": Song | null,
  "playing": true,
  "positionMs": 61234,
  "durationMs": 215000,
  "shuffle": false,
  "repeat": "off" | "one" | "all",
  "volume": 0.8,
  "muted": false,
  "favorite": true,
  "serverTimeMs": 1792962852990
}
```

The position is a sample: while `playing`, extrapolate it from the moment the event arrived. Don't use `serverTimeMs` for that, because the two clocks differ.

### `GET /queue` → Queue

```json
{"current": {"key": "q:…", "song": Song} | null, "upcoming": [{"key": "q:…", "song": Song}], "canReorder": true}
```

`upcoming` is in play order (shuffle included). The `key` names a queue entry in the commands below. `canReorder` is false under shuffle.

### Song

```json
{"id": 7337844665738472311, "title": "…", "artist": "…", "album": "…", "albumId": …, "durationMs": 120032, "art": "7337844665738472311-0-0"}
```

Ids are 63-bit numbers: keep them as `Long`, not `Int`, and not a JSON double in JavaScript. `art` changes whenever the cover does, so use it as the cache key for covers.

### `GET /library`

```json
{
  "version": 3,
  "songs": [Song],
  "albums": [{"id": …, "title": "…", "artist": "…", "songIds": [ … ]}],
  "artists": [{"name": "…", "albumCount": 2, "songIds": [ … ]}],
  "playlists": [{"id": …, "name": "…", "songIds": [ … ]}],
  "favorites": [ … ]
}
```

Songs come sorted by title. Albums and artists are in the PC's order.

### `GET /cover/{songId}?size=600`

The track's cover as JPEG, at most `size` px on a side (64–1200). `404` means the track has no cover. The response may be cached forever under the song's `art` key.

### `GET /lyrics?songId=…`

```json
{"songId": …, "offsetMs": 0, "type": "synced", "lines": [{"timeMs": 12340, "text": "…"}]}
{"songId": …, "offsetMs": 0, "type": "plain", "lines": ["…", "…"]}
{"songId": …, "type": "instrumental" | "none" | "error"}
```

- Show a synced line at `timeMs + offsetMs`.
- The PC fetches lyrics from LRCLIB if it hasn't yet, so the first request for a track can take a few seconds.
- `error` means the PC couldn't reach LRCLIB.

### `POST /command`

`{"action": "…", …}` → `{"ok": true}`, or `400 {"ok": false}` for an unknown action or bad arguments.

| action | arguments |
| --- | --- |
| `toggle`, `play`, `pause`, `next`, `previous` | |
| `seek` | `positionMs` |
| `seekBy` | `deltaMs` (negative goes back) |
| `volume` | `value` 0..1 (also unmutes) |
| `mute`, `shuffle`, `repeat` | toggles; `repeat` cycles off → all → one |
| `shuffleAll` | |
| `playSongs` | `ids` [song ids], `index` to start at, `shuffle` (optional) |
| `playNext`, `addToQueue` | `ids` |
| `playQueueEntry`, `removeQueueEntry` | `key` |
| `moveQueueEntry` | `key`, `position` (0 = right after the current track) |
| `favorite` | `songId` (toggles) |
| `lyricsOffset` | `deltaMs`, or `reset: true` |

The result of a command arrives as events. Don't wait for it in the reply.
