# Rustym v1.2.6 🎵

**Crossfade has been removed — it was not stable enough to ship.**

## 🗑️ Removed
- **Crossfade**, including the setting, the fade-length slider and the "Crossfading…" indicator.

  Multiple songs playing at once needs two audio players running side by side, and on a streaming
  source (where every song change involves URL resolution and buffering) that hand-off proved
  unreliable in practice: depending on the network, the output device and the phone, transitions
  could come out with a silent beat in the middle, or replay the first seconds of the next song.

  Four revisions were attempted (1.2.2 → 1.2.5) — a hand-over at the exact blend position, a
  dual-player engine with no hand-over, a tail player with a proven-rendering gate and a blend
  driven by media position instead of the clock — and none of them was consistently smooth on
  real devices. Rather than ship a feature that breaks at random, the queue now transitions the
  normal Media3 way: songs play one after another, gaplessly where the device allows it, and a
  skip is instant.

  Nothing else changed. Playback, the queue, effects, caching and the media session are untouched,
  and songs that were played with crossfade on keep working — the stored setting is simply ignored.

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.2.5...v1.2.6
