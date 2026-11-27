# Rustym v1.1.7 🎵

**No more repeated audio after the blend** — the next song continues exactly where the crossfade left it, down to the sample.

---

## 🔧 Fixed
- **The next song replayed its last 1–2s after the blend.** Two causes, both fixed:
  1. The fade-out/fade-in ramp ran on timer steps that overshoot, so the blend actually played ~1–2s more than the fade setting. The gain curve is now derived from **actual elapsed time**, so the blend is exactly the fade length you set
  2. At the handover the next song's shadow player **kept playing forward** while the main player started at the older captured position — replaying that stretch. The shadow is now **frozen at the exact commit point**; the main player takes over from precisely where the blend stopped, at the exact blend volume, and finishes the fade-in on its own
- Blend-accurate commit also means the progress bar shows the true position after the transition

## ♻️ Kept
- Artwork/title switch only when the previous song truly ends · equal-power DJ blending · manual skips instant · "Crossfading…" indicator · no trimming of the previous song's audio

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.1.6...v1.1.7
