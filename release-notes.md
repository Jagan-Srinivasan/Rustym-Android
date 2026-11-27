# Rustym v1.0.8 🎵

**Critical fix: playback works again.** v1.0.7's crossfade accidentally broke all playback commands on some devices — this release repairs it and keeps the crossfade.

---

## 🔧 Fixed
- **Music not playing / skip not responding** after updating to v1.0.7 — a change in how the player session handled command requests made every play/seek/set-queue call from the app fail silently. This is fully fixed; playback, skipping and queueing work exactly as before v1.0.7.
- Manual seek/skip during a crossfade now cleanly hands playback back to the main player (handled through the player's own seek events instead).

---

## ✨ Still here (from 1.0.x)
- 🎚️ **True crossfade** — songs melt into each other (Settings → Playback → Skip fade)
- 👆 **Swipe the Now Playing card left / right** to change songs
- 🎚️ **Swipe up / down on the album art** for volume — blinks at 0 until you raise it
- 🤖 **Auto-update & install** in the background (Settings → Updates)
- 🎓 **One-time gesture tutorial** · 📝 **lyrics switch** · 🧹 **cleaner Settings** · 🔄 **startup update check**

---

## 📲 Install
Download **Rustym.apk** below and install it, or let **Auto-update & install** bring it over by itself.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.0.7...v1.0.8
