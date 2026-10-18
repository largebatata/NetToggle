# Third-party notices

NetToggle source retains the Apache License, Version 2.0 in [LICENSE](LICENSE). Dependency licenses remain independent of the project's license. No dependency or author endorsement is implied.

The following runtime versions come from the actual Gradle `debugRuntimeClasspath` report for this candidate:

| Component | Version | License / upstream |
|---|---|---|
| Shizuku API, Provider, AIDL and Shared | 13.1.5 | MIT; Copyright (c) 2021 RikkaW. [Source and license](https://github.com/RikkaApps/Shizuku-API). Full text: [Shizuku-API-MIT.txt](licenses/Shizuku-API-MIT.txt) |
| Kotlin standard library | 2.2.10 | Apache-2.0, JetBrains. [Upstream license](https://github.com/JetBrains/kotlin/blob/v2.2.10/license/LICENSE.txt) |
| JetBrains annotations | 13.0 | Apache-2.0, JetBrains. [Upstream](https://github.com/JetBrains/java-annotations) |
| AndroidX annotation | 1.3.0 | Apache-2.0, Android Open Source Project / Google contributors. [Upstream](https://android.googlesource.com/platform/frameworks/support/) |

The Apache-2.0 text and Shizuku MIT text are also stored under `app/src/main/assets/licenses/`, so they travel with the APK without adding a UI feature, service or runtime library. Public Android framework APIs and system color resources are used; no Material Components or Compose dependency was added.

JUnit 4.13.2 and its test dependencies are used by local / CI unit tests and are not packaged in the APK. Gradle, AGP and GitHub Actions are build tools rather than app runtime dependencies; their respective upstream licenses apply. Build caches, downloaded tools and third-party APKs are excluded from the source archive.
