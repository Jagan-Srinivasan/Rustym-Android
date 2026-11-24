# Rustym for Android

Ad-free YouTube Music player for any Android phone or tablet (Android 8.0+). One universal APK:
it contains no native code, so it runs on arm64, armv7, x86 and x86_64 devices alike.

## Features
- **Home** - trending songs and your recently played
- **Search** - songs from YouTube Music, with queue and favourite buttons
- **Device** - plays music stored on your phone (see below)
- **Library** - favourites, play history, and (when signed in) your YouTube Music liked songs and playlists
- **Google sign-in** - optional; sign in to see your own library, and hearts sync to YouTube Music "Liked songs"
- **Player** - full-screen now-playing with seek bar, shuffle, repeat, queue, favourite
- **Background playback** - keeps playing with the screen off; notification, lock-screen, Bluetooth and
  headset-button controls; pauses when headphones are unplugged; skips tracks that fail to load
- No account, no API key, no ads


## Legal disclaimer & terms of use

**Rustym is free, open-source software, provided "AS IS", with no warranty of any kind.** Read this
section carefully before using or distributing the app.

**1. What Rustym is.** Rustym is a personal media player for Android. It is a specialised *client*
that presents publicly available content from YouTube Music and your own device in a different
interface, the same way a web browser does. It adds nothing to the content it shows, holds no
database of songs, and streams audio **directly from the platform's own servers to your device**.

**2. No hosting of copyrighted material.** The Rustym project does not host, upload, distribute,
store or re-transmit any audio, video, lyrics or other copyrighted media on its own or on any
third-party server controlled by the developer. All content available through the app lives on the
servers of the platforms it connects to (YouTube/YouTube Music, lyric providers, etc.) and remains
the property of its respective copyright owners. Because no copyrighted media is hosted here,
copyright takedown requests for audio or video content cannot be actioned by this project — contact
the platform actually serving the content instead. Questions about this code base itself may be
sent to **jaganrmkcet@gmail.com**.

**3. Intended use.** Rustym is intended strictly for **personal, non-commercial use**. It contains
no advertising, no monetisation, no paid features and no revenue of any kind. You may not sell it,
bundle it with paid products, or operate it as a service for others.

**4. Your responsibility as a user.** You are solely responsible for ensuring that your use of
Rustym complies with (a) the terms of service of the platforms you access through it, and (b) the
copyright laws of your country, including any personal-use or private-copying exceptions you may
rely on. Accessing a platform through a third-party client may be contrary to that platform's terms
of service; if that matters to you, do not use those parts of the app. The developer does not
encourage, support or condone piracy, and gains nothing from how you use the app.

**5. Platform changes.** The app talks to unofficial interfaces of third-party platforms. Those
platforms may change or restrict them at any time, which can break features without notice. That is
an accepted reality of this kind of client, not a defect the developer can or will "fix" forever.

**6. Your data.** Sign-in sessions are stored **only on your device**, encrypted with Android
Keystore. Nothing about you is sent to the developer — there is no analytics, no crash reporting
service and no telemetry. Playback history, favourites and caches live in your app's private
storage and can be erased from **Settings → Data & storage** at any time.

**7. No affiliation.** Rustym is not affiliated with, endorsed by, or connected to Google, YouTube,
YouTube Music, Deezer, LRCLIB, NetEase, KuGou, Lyrics.ovh or any other platform or provider it can
reach. All product names and trademarks belong to their respective owners and are used only to
describe functionality.

**8. Limitation of liability.** To the maximum extent permitted by law, the developer is not liable
for any damages, account restrictions, data loss or legal consequences arising from your use of the
app. If you do not accept these terms, do not use or redistribute the software.


## Google sign-in
Tap **Sign in** (Home or Library). You get Google's real sign-in page inside the app, so password, 2-step
verification and phone prompts all work. When the YouTube session appears, the app checks it with YouTube Music and
stores it **encrypted on the device** (Android Keystore). Sign out any time from Library.

- Why not the native "Sign in with Google" button? That only proves who you are; it cannot give access to a YouTube
  Music library. The web session is what the YouTube Music site itself uses, so that is what Rustym uses.
- If Google shows "This browser or app may not be secure", tap **Trouble?** and paste the `Cookie` header copied from
  music.youtube.com on a computer (steps are shown in the dialog).
- Without signing in, search, playback, local music and favourites all still work.

## Local music
Open the **Device** tab:
- **Allow access to music** - lists every song on the phone (Android 13+: "Music and audio" permission; older: storage).
  Filter, play all, shuffle, add to queue.
- **Pick files** - choose songs with the system file picker; needs no permission at all.
- **Open with Rustym** - from any file manager or app, open an audio file with Rustym and it plays immediately.

## Get the APK (no Android Studio needed)
1. Create a new empty GitHub repository and upload everything in this folder (keep the `.github` folder).
2. Keep the `.github/workflows/build-apk.yml` file at the repository root. Open the **Actions** tab -> **Build Rustym APK** -> **Run workflow** (it also runs on pushes to `main` or `master`).
3. When it finishes (about 5-8 minutes), open the run and download the **Rustym-apk** artifact -> `Rustym.apk`.
4. Copy it to the phone, open it, and allow "Install unknown apps" for your file manager/browser when asked.

## Build locally
Install Android Studio (or JDK 17 + Android SDK 35) and Gradle 8.9, then:

    gradle :app:assembleRelease

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Android Studio: File -> Open this folder.

## Notes
- The APK is signed with the included `app/rustym.keystore` (password `rustym123`) so updates install over
  older builds. For a public/Play Store release, create your own key and change `signingConfigs` in `app/build.gradle.kts`.
- Streams are resolved with [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor). YouTube changes
  often; if search or playback stops working, bump the version in `app/build.gradle.kts`
  (`com.github.teamnewpipe:NewPipeExtractor:v0.27.1`) to the latest release and rebuild.
- This project is for personal use. Respect YouTube's terms and the rights of artists.


## Phase 1 additions (1.3.0)

- Animated playback visualizer on the Now Playing screen.
- Smart queue extension: when the queue is nearly exhausted, Rustym searches for more tracks by the current artist and appends unseen results.
- Listening statistics with total tracked playback time and a persistent most-played list.
- More robust LRCLIB LRC timestamp parsing, including multiple timestamps per line and `[offset:]` metadata.

## Phase 2 playback improvements

- Online liked tracks are prefetched into Rustym's persistent app-specific audio cache when the liked library is loaded; newly liked tracks are also queued for caching.
- The ExoPlayer data-source pipeline reads from the disk cache first and streams/cache-fills on a miss. Cache entries use stable YouTube video IDs rather than temporary stream URLs.
- The cache uses an LRU eviction policy with a 2 GiB ceiling. Android's app-specific storage is used, so clearing Rustym's app data or uninstalling the app removes the cache.
- Up to seven tracks after the current queue item are prefetched in the background. The first seven search results are also warmed after a successful search. Failed prefetches do not interrupt playback.
- Lyrics lookup now tries Unison (video-ID lookup), Better Lyrics' public KuGou LRC endpoint, LRCLIB, and lyrics.ovh in fallback order. Synced lyrics are preferred over plain text and cached per track. A provider/network failure is not stored as a permanent no-lyrics result.
- Better Lyrics TTML results are normalized into the same timestamped line format as LRC. The player follows the active line with animated scrolling; long instrumental gaps show three softly pulsing dots instead of holding the previous lyric on screen.
- The full-screen player no longer shows the decorative audio visualizer. It now has a dedicated lyrics panel with the detected source and a manual LRC/plain-text editor.

Prefetching uses network data and device storage. The 2 GiB LRU cap bounds disk usage; users should be aware that loading a large liked library can consume mobile data. YouTube stream availability and third-party lyrics coverage remain dependent on their respective services.


## Lyrics provider notes

Unison is a public read API keyed by the YouTube video ID. Better Lyrics' KuGou endpoint is used for line-synced LRC results; the main TTML endpoint may require an API key for uncached songs, so Rustym does not depend on it. LRCLIB and lyrics.ovh remain independent fallbacks. LyricsPlus and YouTube Music are shown in the supplied reference as provider choices, but neither currently exposes a documented, stable public lyrics endpoint suitable for a direct unauthenticated integration in this app; they are not falsely represented as active providers.
