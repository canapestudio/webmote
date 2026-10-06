# Webmote

A fast, private Android remote for LG TVs running webOS (2018 models and later).

Open it and press a button, or don't open it at all: use a Quick Settings tile, a home-screen widget
or your phone's volume keys. No account, no analytics: Webmote talks only to your TVs, on your own
network.

Website: <https://webmote.canapestudio.app>

## Features

- **Remote:** D-pad with OK, Back, Home, Settings and Mute around it, a volume slider that follows
  the TV, a brightness slider, media keys (they follow YouTube, Netflix and other players), inputs,
  a number pad with channels and colour keys, and a live keyboard.
- **Picture:** brightness, contrast, colour and energy saving. If a TV ignores picture changes,
  Webmote greys the controls out instead of pretending they work.
- **Touchpad and apps:** a Magic Remote-style touchpad (or point with the phone itself) and a grid
  of the TV's apps with pinned favourites.
- **Without opening the app:** Quick Settings tiles (Power, Mute, Screen off, Open remote), two
  home-screen widgets, a notification with controls, and the phone's volume keys, even when locked.
- **Simple mode:** one screen of large, labelled buttons, for anyone who finds a full remote too
  much.
- **Turn the TV on** with Wake-on-LAN, **several TVs**, English and French.

## Requirements

- An LG TV with webOS 4 or later (2018 onwards), on the same Wi-Fi network as the phone.
- Android 8.0 or later.
- To turn the TV on from the phone: "Turn on via Wi-Fi" (Settings → General → Mobile TV On) or, on
  2025 models and later, Wake on LAN (Support → IP control settings).

## Building

You need JDK 17 and the Android SDK (set `sdk.dir` in `local.properties`, or `ANDROID_HOME`).

```sh
./gradlew :core:test :app:testDebugUnitTest   # tests
./gradlew :app:assembleDebug                  # debug APK in app/build/outputs/apk/debug/
```

The project has two modules:

- `core`: the TV session (pairing, the encrypted SSAP connection, certificate pinning, the pointer
  socket, Wake-on-LAN, discovery). Plain Kotlin with no Android dependencies, tested against a fake
  TV.
- `app`: the Compose UI, the keep-alive service, tiles, widgets and the phone's volume keys.

To try the app without a TV, run the fake TV on your computer and point the emulator at it:

```sh
./gradlew :core:runFakeTv          # serves a fake TV on 127.0.0.1:3001
adb reverse tcp:3001 tcp:3001      # then pair with 127.0.0.1 in the app
```

The website lives in `site/` (Astro); see [site/README.md](site/README.md).

## Privacy

Webmote has no account, no analytics, no crash reporting and no ads SDK. It connects only to your
TVs on your local network, over an encrypted connection pinned to each TV's certificate. The privacy
policy is in [docs/privacy-policy.md](docs/privacy-policy.md) and at
<https://webmote.canapestudio.app/privacy/>.

## Contributing

Issues are welcome; pull requests aren't accepted yet. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

Copyright (C) 2026 Canapé Studio.

Webmote is free software: you can redistribute it and/or modify it under the terms of the GNU
General Public License, version 3, with an additional permission for linking with the Google Play
libraries. See [LICENSE](LICENSE) and [NOTICE](NOTICE), which also credits the projects whose
protocol details Webmote ports.

LG and webOS are trademarks of LG Electronics Inc. Webmote is an independent app, not affiliated
with, endorsed or sponsored by LG Electronics.
