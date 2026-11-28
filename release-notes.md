# Rustym v1.2.5 🎵

**Crossfade rebuilt around one rule: audio is only ever handed over once the receiving side is proven to be sounding.**

## 🔧 Fixed
- **The silent beat between songs.** The old engine muted the current song *before* the tail player was actually outputting anything, and then waited (up to 2.5s) for the next song to finish buffering. Both players were buffering at the same instant, so nothing was audible. Now the tail player starts first, its position is watched until it visibly advances, and only then is the song handed to it.
- **The fade finishing in the wrong place.** The blend now runs on the tail's *media position*, not on wall-clock time, so it ends exactly when the song's real audio ends — even if buffering makes the blend shorter than requested. A short fade always beats a fade into silence.
- **The song's first seconds playing twice.** The next song is now started exactly once, at its own 0:00, by the player that owns the queue. It is never seeked, re-prepared or handed back, and every watcher that could mistake a natural advance for a skip is gone.
- A fade that cannot be completed cleanly now backs off and lets the song play out, instead of restarting anything. It is also allowed to retry later in the track rather than being written off.

## ♻️ Crossfade engine
- Tail player gets its own audio pipeline: **no silence skipping** (it would jump the tail forward and desync the blend) and the same EQ/bass/surround settings, so the outgoing tail does not lose the tone you set mid-fade
- **Your volume is respected** — the blend ramps around the player's actual volume; it no longer snaps back to 100% afterwards
- Pause during a fade holds both players and the blend clock together; resuming finishes the fade where it stopped
- Scrubbing or skipping during a fade closes the blend over ~300ms instead of cutting it
- Repeat-one and the last track in a queue never fade

## 🧪 Verified
The scheduler was simulated across 11 cases (fast/slow network, incoming buffer delays past the end of the tail, YouTube container padding, 1s–8s fades, 2× speed, pause before and during the blend, tail player that never renders). No silent window, no repeated content, and the blend always lands on the song's real ending. **On-device feel still needs your ears** — watch `adb logcat -s RustymCrossfade` for `plan → tail ready → handed over → blend from → blend released`.

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.2.4...v1.2.5
