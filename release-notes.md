# Rustym v1.1.8 🎵

**The next song now starts from its true beginning after the blend.**

---

## 🔧 Fixed
- **The song's opening seconds could be skipped.** During the anchor wait the next song sat *playing silently* on the shadow player, so its clock consumed the first ~1–2s of audio that you never heard — and the handover continued past them. The shadow player now stays **paused** during the wait and starts from its own 0:00 exactly when the blend begins, so:
  - the fade-in covers the song's real opening
  - the handover continues at ~the fade length into the song
- Verified in logs: ramp now runs exactly the set fade length (elapsed-time gain curve, `fEnd=1.0`), handover continues at the blend position with no repeat and no skip

## ♻️ Kept
- Artwork/title switch only when the previous song truly ends · equal-power DJ blending · manual skips instant · "Crossfading…" indicator · no trimming of the previous song's audio

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.1.7...v1.1.8
