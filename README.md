# Webmote

A fast, private Android remote for LG TVs running webOS (2018 models and later).

No account, no analytics: Webmote talks only to your TVs, on your own network.

## Requirements

- An LG TV with webOS 4 or later (2018 onwards), on the same Wi-Fi network as the phone.
- Android 8.0 or later.

## Building

You need JDK 17 and the Android SDK (set `sdk.dir` in `local.properties`, or `ANDROID_HOME`).

```sh
./gradlew :core:test :app:testDebugUnitTest   # tests
./gradlew :app:assembleDebug                  # debug APK in app/build/outputs/apk/debug/
```

The project has two modules:

- `core`: the TV session (pairing, the encrypted SSAP connection, certificate pinning, discovery).
  Plain Kotlin with no Android dependencies, tested against a fake TV.
- `app`: the Compose UI.

To try the app without a TV, run the fake TV on your computer and point the emulator at it:

```sh
./gradlew :core:runFakeTv          # serves a fake TV on 127.0.0.1:3001
adb reverse tcp:3001 tcp:3001      # then pair with 127.0.0.1 in the app
```

## Privacy

Webmote has no account, no analytics, no crash reporting and no ads SDK. It connects only to your
TVs on your local network, over an encrypted connection pinned to each TV's certificate.

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
