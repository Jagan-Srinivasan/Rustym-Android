# Rustym v1.1.1 🎵

**The crossfade fix, verified by the logs.** Diagnostics showed every fade was being killed the instant it started, so songs hard-cut. Root cause found and removed.

---

## 🔧 Fixed
- **Fades were self-cancelling.** ExoPlayer delivers seek events asynchronously, so the fade's own queue-jump was interpreted as "the user skipped" — the blend was aborted after ~1 tick and the volume snapped back to full. That's the blunt cut you heard. The fade now only stops if you skip **past** the faded-into song, pause, or turn crossfade off.
- Full instrumented logging (start → fading-into-item → done / cancelled-with-reason) so any remaining issue is immediately visible.

---

## ✨ Still here
- 🎚️ **Crossfade switch + slider** (Settings → Playback) · 🤫 **Skip Silence** on by default · 👆 swipe card / art gestures · 🤖 auto-update & install · 🎓 one-time tutorial · 🔄 startup update check

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it. Set **Settings → Playback → Crossfade → on, 4–8s**, then play a playlist.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.1.0...v1.1.1
