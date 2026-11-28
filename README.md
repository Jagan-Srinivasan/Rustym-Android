# 🎵 Rustym for Android

A beautiful, ad-free music player for Android 8.0+ with YouTube Music streaming, synced lyrics,
a studio-grade equalizer, 8D audio and a home-screen widget. One universal APK (arm64, armv7,
x86, x86_64) — no native code, no bloat.

---

## ✨ Features

### Playback & player
- **Full-screen Now Playing** — 320dp artwork with rounded corners, song title + artist, heart,
  pill-shaped transport controls (previous / play / next), shuffle, repeat, playback speed
  (0.75×–2×) and a sleep timer.
- **Background playback** — music keeps playing with the screen off via a Media3 media-session
  foreground service: notification controls, lock-screen controls, Bluetooth & headset buttons,
  auto-pause when headphones are unplugged.
- **Smart queue** — when the queue is nearly exhausted, Rustym automatically finds more tracks by
  the current artist and appends them.
- **Output device routing** — tap the headphones icon to see every connected output (speaker,
  wired, Bluetooth, USB) and tap one to route audio there instantly; system default included.

### Sound effects (Settings → Sound, or the tune icon on Now Playing)
- **5-band equalizer** — real-time vertical band sliders (±dB), persisted per app; built on
  Android's native audio session.
- **Bass boost** — 0–100% strength slider.
- **Surround sound** — stereo widening via the platform virtualizer, 0–100%.
- **8D audio** — a custom PCM processor rotates the stereo image around your head (~6.5s per
  rotation). Off = byte-for-byte passthrough, zero overhead.

### Lyrics
- **Multi-source synced lyrics** — resolved per track from Unison, Better Lyrics (KuGou LRC),
  LRCLIB (incl. `TTML` normalisation, multi-timestamp lines, `[offset:]` metadata), NetEase
  Cloud Music, KuGou direct and Lyrics.ovh; synced LRC preferred over plain text.
- **Karaoke-style player** — animated scrolling to the active line; long instrumental gaps show
  pulsing dots; a small lyric line sits under the artwork.
- **Built-in editor** — long-press the lyric area (or the pencil) to edit LRC/plain text manually.
- Cached per track; nothing is fetched for songs with no lyrics — the UI simply stays clean.

### Home & search
- **Tinted home page** — the greeting header, chips and cards adopt the colour of the **last
  played song's artwork** (Palette extraction), applied app-wide and even on cold start.
- **Mood chips** — All · Relax · Sleep · Energize · Romance re-fetch "Quick picks" instantly.
- **Quick picks** — large horizontal artwork cards with gradient overlays; tap to play, "Play all"
  shuffles the set.
- **Time-aware greetings** — morning / afternoon / evening / late-night messages.
- **Live search suggestions** — real YouTube Music autocomplete while you type, plus recent
  search history and mood chips on the explore screen.

### Library & sign-in
- **Device tab** — plays your phone's local music (Android 13+ "Music and audio" permission),
  with file-picker mode and "Open with Rustym" from any file manager.
- **Library** — favourites, play history and listening statistics (songs played, total time,
  most-played list).
- **Optional Google sign-in** — shows a consent popup first; then reads your YouTube Music liked
  songs, playlists and recommendations. Hearts on online tracks sync to your real "Liked songs".
  Fully optional — everything else works without it.

### Home-screen widget
- **Rustym player widget** — artwork, song title/artist and previous / play-pause / next buttons
  that control the real media session even with the app swiped away. Tap artwork to open the app.
  Auto-updates on every track and play-state change.

### Look & feel
- **Material 3** redesign with your choice of theme (Midnight, AMOLED, Light), 8 accent colours,
  optional glass effects, and **Material You dynamic colours** from your wallpaper (Android 12+).
- Floating glass mini-player, rounded navigation bar, rounded row artwork, accent-dotted section
  titles.

### Data & storage (Settings → Data & storage)
- See exactly what's stored: temporary cache, **cached songs (count + MB)** and **saved lyrics
  (count + KB)** — with one-tap Clear buttons for each, plus listening history and play-stats
  reset.

---

## 🔍 How Rustym works

**Reads (public content only):**
- Audio streams — resolved from YouTube Music and streamed directly to your device via
  [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor); served from a local LRU
  disk cache when already downloaded.
- Metadata, search, suggestions — YouTube Music's public web endpoints.
- Lyrics — the public read APIs listed above, matched by video ID / title / artist / duration.

**Sends:** nothing to the developer — there is **no analytics, no crash-reporting service, no
telemetry, no API key and no account requirement**. The only data that ever leaves the device are
the same requests your browser would make to YouTube Music and the lyrics providers, plus — if you
explicitly sign in — calls that read or like songs **on your own YouTube Music account**.

**Stores (all on-device, private app storage):**
- Your Google session, **encrypted with Android Keystore** (only if you sign in).
- Favourites, play history and statistics.
- An offline audio cache (LRU, 2 GiB ceiling) so streamed songs replay without re-downloading —
  erasable in Settings, or by clearing app data / uninstalling.

**Never:** uploads anything, shares your data with third parties, or runs ads.

---

## 🔐 Google sign-in

Tap **Sign in** (Home or Settings). A consent popup explains exactly what it is used for before
Google's real sign-in page opens inside the app — password, 2-step verification and phone prompts
all work. The YouTube session is verified with YouTube Music and stored **encrypted on the device**.
Sign out any time from the Library.

- Why not the native "Sign in with Google" button? That only proves who you are; it cannot read a
  YouTube Music library. The web session is what music.youtube.com itself uses — so that is what
  Rustym uses.
- If Google shows "This browser or app may not be secure", tap **Trouble?** and paste the `Cookie`
  header copied from music.youtube.com on a computer (steps are shown in the dialog).
- Without signing in, search, playback, local music and offline favourites all still work.

## 🎼 Lyrics provider notes

Unison is a public read API keyed by the YouTube video ID. Better Lyrics' KuGou endpoint is used
for line-synced LRC; LRCLIB and lyrics.ovh are independent fallbacks; NetEase and KuGou are used
directly with smart title/artist/duration matching. LyricsPlus and YouTube Music's own endpoint are
not integrated because neither exposes a documented, stable public API for unauthenticated use —
the app does not claim providers it does not actually use.

## 📝 Notes

- The APK is signed with the included `app/rustym.keystore` (password `rustym123`) so updates
  install over older builds. For a public/Play Store release, create your own key and change
  `signingConfigs` in `app/build.gradle.kts`.
- YouTube changes often; if search or playback stops working, bump the NewPipeExtractor version in
  `app/build.gradle.kts` (`com.github.teamnewpipe:NewPipeExtractor:v0.27.1`) to the latest release
  and rebuild.
- Prefetching uses network data and device storage; the 2 GiB LRU cap bounds disk usage.

---

## ⚖️ Legal disclaimer & terms of use

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
YouTube Music, LRCLIB, NetEase, KuGou, Lyrics.ovh or any other platform or provider it can reach.
All product names and trademarks belong to their respective owners and are used only to describe
functionality.

**8. Limitation of liability.** To the maximum extent permitted by law, the developer is not liable
for any damages, account restrictions, data loss or legal consequences arising from your use of the
app. If you do not accept these terms, do not use or redistribute the software.
