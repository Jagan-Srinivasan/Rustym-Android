# Rustym v1.1.5 🎵

**Crossfade engine v4** — the artwork/title now stay on the previous song until it truly ends, and the mid-blend mute is gone.

---

## 🔧 Fixed
- **Album artwork switched too early.** The queue used to advance at the handover, while the previous song was still fading. Now the blend is inverted: the **previous song stays on the main player** (artwork, title, lyrics, progress all stay on it) and the **next song waits ready on a shadow player, held silent**. The queue only advances when the previous song has actually finished — then the next song takes over seamlessly at the exact position the blend reached
- **The ~1s mid-blend mute.** The outgoing tail's silence-trimmer was too aggressive (−30dB) and fast-forwarded through the song's own quiet fade-out, drying the tail mid-blend. The outgoing song now never gets trimmed at all — it plays its real audio to the end on the main player
- The blend covers the previous song's **real last N seconds** (anchored on its own clock), with the next stream resolved + buffered underneath beforehand

## ♻️ Kept
- Equal-power (cos/sin) DJ blending · manual skips stay instant (no crossfade) · "Crossfading…" indicator from the moment the handover starts · always starts the next song where the blend left off

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.1.4...v1.1.5
