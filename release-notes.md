# Rustym v1.1.3 🎵

**Premium-grade crossfade** — rebuilt following how reference players (SimpMusic's dual-player engine, DJ-standard blending) implement it.

---

## 🔧 Upgraded crossfade engine
- **Equal-power cosine/sine gain curves** replace the linear ramp — the DJ-standard blend where the combined loudness stays constant through the transition instead of dipping in the middle
- **The tail skips its own silence.** YouTube streams often carry 1–2s of silent padding after the last audible sample — that padding *was* the silence you heard inside the blend. The fading-out tail now fast-forwards through any silence ≥0.5s, so the blend contains real audio from both songs
- **Earlier handover with headroom** — the queue jump starts 1.2s before the fade window so the incoming stream's resolve/buffer happens under the outgoing song's audio, not in a hole
- The incoming song always starts at its own 0:00 — never mid-track

## ✨ New
- **"Crossfading…" live indicator** under the seek slider on the Now Playing screen while a blend is running (polled from the playback service, ~3x/second)

---

## ✨ Still here
- 🎚️ Crossfade switch + slider (4s default) · 🤫 Skip Silence on by default · 👆 swipe gestures · 🤖 auto-update & install · 🔄 startup update check

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.1.2...v1.1.3
