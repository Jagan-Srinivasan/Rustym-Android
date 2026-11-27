# Rustym v1.1.2 🎵

**No more silent hole before the blend.** Crossfade now waits for the incoming song to be ready before fading, so the outgoing song keeps playing right up to the handover.

---

## 🔧 Fixed
- **The 1–2s silent gap at transitions** — the fade used to start before the next song's stream was resolved/buffered, so you heard: fade-out → silence → fade-in. Now the outgoing song keeps playing at full volume while the next stream prepares, and the blend only begins once the incoming audio is actually ready. The old song plays to its true end — zero added silence.
- **Never skips the start of a song** if the player clock crept forward while silent — the playhead is reset to 0:00 before the fade-in.
- **Crossfade on more songs** — manual skips (swipe / next button) now also blend songs whose stream duration is unknown.

## ✨ Changed
- Turning the **Crossfade switch on for the first time defaults to 4s**. Once you move the slider, your choice is remembered (toggle off/on keeps it).

---

## ✨ Still here
- 🎚️ Crossfade switch + slider · 🤫 Skip Silence on by default · 👆 swipe gestures · 🤖 auto-update & install · 🔄 startup update check

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.1.1...v1.1.2
