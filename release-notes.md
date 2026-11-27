# Rustym v1.2.4 🎵

**Crossfade rewired — the next song no longer replays its first seconds.**

## 🔧 Fixed
- The old engine handed the next song back to the main player by seeking it to the blend position, which caused a rebuffer gap and replayed audio still queued in the output (worst on Bluetooth).
- New dual-player design with **no hand-off**: the main player simply moves on and plays the next song normally from 0:00, while a shadow player carries the old song's tail. Equal-power blend, then the shadow is released.
- Pause, manual skip and queue changes during a fade are handled cleanly; repeat-one doesn't crossfade.

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.
