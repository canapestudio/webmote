# Webmote

A fast, private Android remote for LG TVs running webOS (2018 models and later).

## Building

You need JDK 17 and the Android SDK (set `sdk.dir` in `local.properties`, or `ANDROID_HOME`).

```sh
./gradlew :core:test :app:testDebugUnitTest   # tests
./gradlew :app:assembleDebug                  # debug APK in app/build/outputs/apk/debug/
```

The project has two modules:

- `core`: the TV session. Plain Kotlin with no Android dependencies, tested against a fake TV.
- `app`: the Compose UI.

## Contributing

Issues are welcome; pull requests aren't accepted yet. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

Copyright (C) 2026 Canapé Studio.

Webmote is free software: you can redistribute it and/or modify it under the terms of the GNU
General Public License, version 3, with an additional permission for linking with the Google Play
libraries. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

LG and webOS are trademarks of LG Electronics Inc. Webmote is an independent app, not affiliated
with, endorsed or sponsored by LG Electronics.
