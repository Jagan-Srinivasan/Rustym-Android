# Rustym v1.2.3 🎵

**Root cause of the repeating song opening finally fixed — for every output (speaker, wired, Bluetooth).**

---

## 🔧 Fixed
- **The next song restarted from 0:00 on some transitions.** Three independent watchers poll the queue during a blend (a 200ms ticker, a 50ms blend loop, and the end-of-song listener). When a poller noticed the song change a moment *before* the listener did — the timing varies per transition — it mistook the natural end-of-song advance for a manual skip, threw the blend away, and the player continued the next song **from 0:00 at full volume**, replaying the fade-in portion you had just heard (the "1–2s repeated with a tiny pause").
- All three watchers now recognize the natural advance as the **takeover moment**: the next song continues exactly where the blend left it — no restart, no replay, on speaker and wired just like on Bluetooth
- This also explains why the repeat was intermittent and appeared on every kind of output

---

## ♻️ Crossfade engine
- Artwork/title switch when the previous song truly ends · continuous handover · exact-length equal-power blends · instant manual skips (still instant — only the *natural* advance is a takeover) · "Crossfading…" indicator

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.2.2...v1.2.3
