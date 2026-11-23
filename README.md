<div align="center">

# 🔦 Shake Torch

**Chop twice. Light.**

A tiny, ad-free Android flashlight that turns on with the classic Motorola
chop-chop gesture — and refuses to die quietly in the background.

![Version](https://img.shields.io/badge/version-1.5-7C4DFF)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Size](https://img.shields.io/badge/size-~25_KB-blue)
![Ads](https://img.shields.io/badge/ads-none-success)
![Internet](https://img.shields.io/badge/internet_permission-none-success)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

</div>

---

## Why this exists

The Play Store's shake-torch apps filled up with ads and trackers. This one
was written from scratch to be the opposite: ~25 KB, **no internet permission
at all** (so it *cannot* phone home), no ads, no analytics, no accounts.
Just a gesture and a light.

## ✨ Features

- **Double-chop gesture** — two quick chops toggle the torch, screen off or on,
  with a pocket guard that ignores movement while the proximity sensor is covered
- **Haptic signatures** — a double pulse when the light turns on, one firm buzz
  when it turns off, so you know without looking
- **Shake intensity slider** — a wide 1.3 g – 3.8 g range with the live firing
  threshold shown as you drag
- **Customizable dark mode** — System / Light / Dark, plus five accent colors
  (purple, blue, pink, teal, orange) that restyle the whole app
- **A card-based, minimal UI** — one big torch button, everything else tucked
  into calm, rounded cards

## 🛡️ It stays alive

Background flashlight apps are famous for silently dying. Shake Torch stacks
every survival layer Android offers:

| Layer | What it does |
|---|---|
| Foreground service | Sticky, with a persistent notification |
| Boot receiver | Starts itself after a reboot, if you left it on |
| Self-revive | Relaunches seconds after being swiped away or killed |
| Watchdog | A 15-minute alarm chain that revives it if the system wins anyway |
| Root protections *(optional)* | One tap whitelists the app against Doze, pins its standby bucket to *active*, allows background runs, and disables the Android phantom process killer |

On OnePlus / OxygenOS it also needs **Battery usage → Unrestricted** and
**Auto launch → on**; the app walks you to the right settings page.

## 📲 Install

1. Download the latest `ShakeTorch-x.x.apk` from Releases
2. Open it and allow the install (it's signed, and installs cleanly over
   older versions)
3. Open the app, allow notifications, switch on **Shake detection**
4. Set battery to **Unrestricted** (and Auto launch on OnePlus)

## 🔐 Permissions

Six, all explainable:

| Permission | Why |
|---|---|
| `FLASHLIGHT` | The torch itself |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Keep listening for the chop |
| `POST_NOTIFICATIONS` | The "listening" notification |
| `VIBRATE` | Haptic on/off signatures |
| `RECEIVE_BOOT_COMPLETED` | Start after reboot |

No `INTERNET`. Ever.

## 🛠️ Build from source

No Gradle — the build drives the Android SDK tools directly:

```bash
./build.sh   # needs a JDK 17 and Android SDK platform/build-tools 34
```

Produces a signed `ShakeTorch-v<VERSION>.apk` in the project root, where `<VERSION>` is the version name set in `build.sh`.

## 📄 License

MIT — do what you like, keep the notice.

<div align="center">
<sub>Built with Muse, one chop at a time.</sub>
</div>
