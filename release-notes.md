# Rustym v1.1.0 🎵

**Crossfade you can actually hear.** The fade engine had a bug that cut every fade the moment it started — this release fixes it, gives crossfade a proper on/off switch, and turns Skip Silence on by default.

---

## 🔧 Fixed — why you couldn't hear the crossfade
- The fade was **aborting instantly on every transition**: the fade loop watched `isPlaying`, which goes false for a split second while the incoming track buffers — so the blend collapsed before it was audible
- The fade now watches the real "user paused" signal (`playWhenReady`) and rides through buffering, so songs genuinely overlap for the full fade length

## ✨ What's new
- **Crossfade is now a switch + slider** (Settings → Playback): flip it on, then pick the fade length 1–8s. Turning the switch off brings back hard cuts instantly
- **Skip Silence is ON by default** for new installs — quiet intros, long outros and gaps are skipped without touching a setting (existing users keep their choice)

---

## ✨ Still here from 1.0.x
- 🎚️ True crossfade on skips and natural ends · 👆 swipe card to change songs · 🎚️ volume swipe (blinks at 0) · 🤖 auto-update & install · 🎓 one-time tutorial · 📝 lyrics switch · 🔄 startup update check

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it. Then: **Settings → Playback → Crossfade → on, 4s** — and play a playlist.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.0.9...v1.1.0
