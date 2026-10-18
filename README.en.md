# NetToggle

[简体中文](README.md) · [Apache-2.0](LICENSE) · **0.9.0-beta.1**, versionCode 8

A free, open-source, lightweight Android app network switch using Shizuku UserService and Connectivity Chain 3. Native Views, system dynamic colors and dark mode; the UI is primarily Chinese.

Features: app network switches, actual system-rule readback, Shizuku connection and a shortcut to Android's data usage settings. **No root, VPN slot, INTERNET permission, traffic collection or persistent background monitoring.** No traffic statistics, DNS filtering, ad blocking or complex firewall features.

## Compatibility and evidence

Minimum installable version: Android 13 / API 33; compile / target SDK 37 / Android 17. This minimum is not a compatibility guarantee. Internal Connectivity commands depend on firmware: NetToggle attempts actual readback, but cannot guarantee compatibility with every firmware. Unknown results cannot enable writes. Only owner user 0 is supported; system apps, this app and low UIDs are excluded.

**Primary device testing has only been completed on Pixel (Android 17). Other Android devices and system versions have not been compatibility-tested.** The user reports four successful manual regressions of the formally signed APK: Shizuku authorization / rule reads, GKD block / restore, Clash Meta coexistence, and actual-state verification / manual recovery after reboot.

Earlier Debug builds also passed manual Wi-Fi, 5G and Shizuku stop / reconnect tests. In Wi-Fi + Clash Meta, GKD was blocked while a browser stayed online and Clash remained connected. These are user-reported manual tests, separate from automated unit tests, Lint and builds. They do not establish universal compatibility or cover every proxy route, mobile-data-with-Clash or shared UID combination. See the [Chinese checklist](TESTING.zh-CN.md).

## Setup

1. Install Shizuku from its [official download page](https://shizuku.rikka.app/download/) and start it through wireless debugging or computer ADB using the [official manual](https://shizuku.rikka.app/guide/setup/). Use the command shown by your installed manager; root is not required.
2. Obtain `NetToggle-0.9.0-beta.1.apk` from a published Release in this repository, verify its SHA-256 and install in user 0. If still a draft, wait for publication. Actions Debug artifacts are not official release APKs.
3. Open NetToggle, tap the Shizuku chip and grant permission. “Shizuku 已授权” requires both current permission and a live UserService. Unauthorized, not started, connecting and disconnected states are distinct.
4. Wait for verification and use a non-critical test app with an online control app.

APK SHA-256:

```text
ef7b770c281b1988549e65ef4427502e02e7abdcc472129e2a1d2d677c46e5b2
```

See [SIGNING.md](SIGNING.md) for the public certificate fingerprint. A differently signed Debug installation usually cannot be replaced directly. Before uninstalling it, restore access for every blocked app, verify new requests and clear unwanted retained denies, then confirm migration. Uninstalling or clearing data does not clear system rules.

## Switches and snapshots

**On means network allowed; off means blocked.** Readback must confirm writes. Errors and timeouts are reported and necessary rules rechecked; repeated writes are gated. Blocking and critical-app changes show risk confirmation.

| Rule | Display |
|---|---|
| Chain 3 enabled, app deny | Blocked, switch off, first in All |
| App allow | Unblocked, switch on |
| Chain 3 disabled, deny retained | Currently unblocked, switch on, explicit notice; tap status to clear |
| History, unread or failed | All only, clearly historical or unknown, disabled switch |

Counts and groups use verified states. Groups sort by name, blocked first in All; search filters the current group. Readback is the last successful snapshot, not continuous network probing.

Private SharedPreferences store successful history, **not DataStore or automatic restoration settings**. Cold start shows marked history while asynchronously checking actual rules, prioritizing previous blocks in batches of at most eight. Cache loading never promotes history to verified state.

Within one process, short background trips, groups, search and scrolling reuse verified snapshots. Cold start, manual refresh, reconnection, inventory changes or returning after ten minutes in the background require a full check. There is no foreground polling. Individual writes recheck affected UIDs and potentially activated retained denies. Refresh after external rule changes.

## Reboot and recovery

**Rules may stop being effective after reboot and Shizuku may need restarting.** GKD was observed to regain access, but whether Chain 3 was disabled, denies cleared, or both is unknown. There is no boot service, automatic restoration or global chain-disable action.

Restart Shizuku, reconnect and verify actual state; historical blocks are not proof of enforcement. Manually turn off a verified switch if blocking is still wanted and test a new request. To restore access, turn on and wait for readback; tap status to clear inactive retained denies. On failure / timeout refresh before deciding. Stopping Shizuku or uninstalling NetToggle does not remove existing rules; restart Shizuku and restore the relevant app with NetToggle when needed.

## Boundaries

- Rules operate on appIds. Shared UIDs, matching appIds and other users can be affected together. Restricting the UI to user 0 and hiding system apps does not remove indirect effects. Blocking critical system apps is discouraged; be cautious with Clash and Shizuku.
- Blocking may enable global Chain 3 and activate other tools' retained denies. Restoring access clears the target and never globally disables the chain.
- No VPN slot or Clash configuration is changed. Rule state is not end-to-end reachability proof for every proxy or system-mediated request.
- No boot receiver or persistent Android Service. Accepted finite I/O may finish after leaving the Activity; Shizuku's non-daemon Binder UserService does not monitor traffic.
- History is excluded from backup. No traffic, request or app-log collection; rows / icons are reused and rule I/O stays off the UI thread.

## Build

Use JDK 21, SDK 37 and Build Tools 36.0.0. AGP 9.3.2, Gradle Wrapper 9.5.0 and Shizuku API / Provider 13.1.5 are pinned. Bytecode targets Java 17; the wrapper distribution has a fixed SHA-256.

On Windows set ANDROID_HOME and JAVA_HOME to installed tools and run `./Build-Debug.ps1` in PowerShell 7 (compatible with 7.6.6). Add `-Offline` only with cached dependencies. The script copies into an ASCII TEMP path for AIDL, runs clean build / unit tests / Debug Lint and returns artifacts to ignored `artifacts/`.

On Linux / ASCII paths:

```bash
bash gradlew --no-daemon --console=plain clean :app:test :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```

`Build-Release-Unsigned.ps1` builds a non-debuggable **unsigned** Release, all available unit tests and both Lint variants. The unsigned output is not the official installable APK; production signing material is not in source.

[Actions](.github/workflows/android.yml) uses commit-pinned actions and read-only repository permissions, validates the wrapper, builds / tests / lints and checks Debug signature / alignment. It uploads only labeled test Debug artifacts and reports, receives no production key and never signs or publishes official Releases. Actual status is recorded in the [publication checklist](RELEASE-REPORT.zh-CN.md).

## License

[Apache-2.0](LICENSE). Dependency notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The [Chinese audit](AUDIT.zh-CN.md) describes privacy boundaries. Never commit passwords, private keys, SDK configuration, caches or personal device records.
