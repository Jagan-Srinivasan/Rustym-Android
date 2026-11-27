# Rustym v1.1.4 🎵

**Crossfade engine v3** — the blend is now anchored to the outgoing song's real last seconds, and manual skips no longer crossfade.

---

## 🔧 Crossfade rework
- **Manual next = instant skip.** Swiping or tapping next jumps straight to the song — crossfade only happens when a song naturally runs into its fade window
- **No more dead air in the blend.** The transition now works in 3 phases:
  1. The queue moves while the outgoing song keeps playing at full volume, and the next stream resolves/buffers under it
  2. The incoming song is held silent until the outgoing song's **real last fade window** is reached (the tail's own clock is used, and its silent padding is fast-forwarded, so the blend never sits in a hole)
  3. Equal-power (cos/sin) blend over exactly the last N seconds of the outgoing song
- The trigger fires earlier with headroom, so buffering never eats the blend
- The incoming song always starts at its own 0:00

## ✨ Indicator
- **"Crossfading…" appears the moment the handover starts — during the previous song**, i.e. you see it while the old song is still playing (it stays through the blend)

---

## ✨ Still here
- 🎚️ Crossfade switch + slider (4s default) · 🤫 Skip Silence · 👆 swipe gestures · 🤖 auto-update & install · 🔄 startup update check

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.1.3...v1.1.4
