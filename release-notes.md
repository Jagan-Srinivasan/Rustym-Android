# Rustym v1.1.6 🎵

**Seamless handover** — the next song now continues exactly where the blend left it, instead of restarting from the beginning.

---

## 🔧 Fixed
- **Next song restarted from 0:00 after the blend.** Root cause: the previous song's end auto-advanced the queue mid-blend, which threw away the blend's progress. Now:
  - the auto-advance at end-of-song is suppressed while a blend is running
  - when the previous song has fully finished, the queue advances deliberately and the player **seeks to the exact position the fade-in reached**, continuing the song where the blend left it
  - the remaining part of the gain curve is finished on the main player while the shadow player bows out — no jump, no restart, no gap
- Works for every early-commit case too (song ending mid-ramp commits early and seamlessly)

## ♻️ Kept
- Artwork/title stay on the previous song until it truly ends · equal-power (cos/sin) DJ blending · manual skips stay instant · "Crossfading…" indicator · previous song's audio never trimmed

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.1.5...v1.1.6
