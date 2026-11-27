# Rustym v1.2.1 🎵

**Update check fixed** — "Check for updates" no longer fails with "Couldn't reach GitHub".

---

## 🔧 Fixed
- **Update check resilience.** The check previously talked only to `api.github.com` with short 8s timeouts — on networks where that host is slow or blocked, the check always failed. Now:
  - **15s timeouts, a User-Agent header, and a retry**
  - **A fallback that doesn't need the API at all**: if the API is unreachable, Rustym probes `github.com/<repo>/releases/latest` (a different host) and reads the newest version straight from the redirect — so the check works even when `api.github.com` is blocked
  - Test pre-releases (rc builds) are ignored — only real releases are offered

---

## ♻️ Everything from v1.2.0
- Verified crossfade: artwork switches when the previous song truly ends, continuous handover (no repetition, no gap), exact-length equal-power blends, instant manual skips, clean pause handling, "Crossfading…" indicator

---

## 📲 Install
Download **Rustym.apk** below, or let **Auto-update & install** do it.

---

**Full changelog:** https://github.com/Jagan-Srinivasan/Rustym-Android/compare/v1.2.0...v1.2.1
