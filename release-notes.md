# Rustym v1.2.2 🎵

**Final fix for the repeating 1–2s after the blend.**

---

## 🔧 Fixed
- **The repeated stretch is the Bluetooth pipeline.** Your earbuds keep ~1.5–2.5s of audio in flight after the player's own clock position, so the handover was resuming *inside* that in-flight tail — you heard it twice. The handover now resumes **2000ms ahead on Bluetooth output** (350ms on wired/speaker): the tail fills the skipped window exactly, so the next song continues with **no repetition and no pause**
- Update-check resilience from v1.2.1 is kept (15s timeouts, retry, github.com fallback, rc builds ignored)

---

## ♻️ Crossfade engine
- Artwork/title switch when the previous song truly ends · continuous handover at the blend position · exact-length equal-power blends · instant manual skips · clean pause handling · "Crossfading…" indicator

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.2.1...v1.2.2
