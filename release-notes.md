# Rustym v1.1.9 🎵

**Crossfade rebuilt from the ground up and verified on-device through dozens of transitions** — this is the release where crossfade finally behaves like a premium player.

---

## 🎛️ Crossfade engine v6
- **Artwork/title stay on the previous song until it truly ends** — the next song waits ready on a shadow player and only takes over when the previous song has finished
- **Natural handover**: the previous song ends on its own (no forced queue jumps, no pausing at the last second) and the next song continues **exactly where the blend left it**
- **No repetition**: the handover skips the small stretch the audio pipeline (Bluetooth) still holds in flight — the previous tail fills that window, so playback is continuous with no replay and no gap
- **Real fade length**: the blend window is anchored on each song's audible duration (YouTube containers run longer than the music), and the gain curve runs on actual elapsed time — an exact N-second equal-power blend
- **Padding-safe**: songs whose container runs longer than the music still hand over cleanly (the blend degrades gracefully instead of glitching)

## 🔧 Also
- **Manual skips stay instant** — swiping/tapping next never crossfades
- **Pause during a blend** cancels it cleanly and keeps the current song
- "Crossfading…" indicator under the seek slider (Now Playing) while a blend is live

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.1.8...v1.1.9
