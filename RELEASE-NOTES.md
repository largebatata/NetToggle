# NetToggle v0.9.0-beta.1

## 中文

首个 Beta 候选。免费、开源的极简 Android 应用联网开关，通过 Shizuku UserService 和 Connectivity Chain 3 读取 / 修改系统规则。支持分组、搜索、明确标记的历史缓存、动态配色、深色模式及系统流量统计入口。

无需 Root，不占用 VPN，不申请 INTERNET 权限，无流量采集、DNS 过滤或常驻后台监控。需要 Android 13+ 及已启动并授权的 Shizuku；最低 SDK 不保证固件接口兼容。

主要真机测试仅覆盖 **Pixel（Android 17）**。用户报告正式签名 APK 四项人工回归全部通过：Shizuku 授权与规则读取、GKD 禁止 / 恢复、Clash Meta 共存、重启后真实核实与手动恢复。此前 Debug 另完成人工 Wi-Fi、5G、Shizuku 停止 / 重连测试。这些不是自动化真机结果，其他 Android 设备和系统版本尚未完成兼容性验证。

**重启后封锁可能失效，Shizuku 可能需要重新启动。** 尚未确定链关闭还是规则清除；本版不自动恢复。重新启动 Shizuku、核实后按需手动禁止 / 允许。共享 UID、appId、多用户可能联动，不建议封锁关键系统应用；系统规则读数不保证所有代理路径效果。

下载下方正式 `NetToggle-0.9.0-beta.1.apk`，核对 SHA-256 后安装，在应用内授权 Shizuku 并等待核实。旧 Debug 签名不同，迁移前先恢复所有受影响应用并清除不需要的保留 deny，再由用户确认卸载旧版并安装正式版。卸载不会主动清理规则。Actions Debug 产物不是官方安装包。

## English

First Beta candidate of a free, open-source minimal Android app network switch using Shizuku UserService and Connectivity Chain 3. Includes actual rule readback, groups, search, clearly historical caches, dynamic colors, dark mode and a shortcut to Android's data usage settings.

No root, VPN slot, INTERNET permission, traffic collection, DNS filtering or persistent background monitoring. Requires Android 13+ and a started, authorized Shizuku installation; the minimum SDK is not a firmware compatibility guarantee.

Primary device testing only covers **Pixel (Android 17)**. The user reports four successful manual regressions of the formally signed APK: Shizuku authorization/rule reads, GKD block/restore, Clash Meta coexistence, and actual-state verification/manual recovery after reboot. Earlier Debug builds also passed manual Wi-Fi, 5G and Shizuku stop/reconnect tests. These are not automated device tests. Other Android devices and system versions have not been compatibility-tested.

**Blocking may stop being effective after reboot and Shizuku may need restarting.** Whether the chain was disabled or rules cleared is unknown. No automatic restoration: restart Shizuku, verify, then manually block/allow as needed. Shared UIDs, matching appIds and other users can be affected together; avoid critical system apps. Rule readback does not prove end-to-end behavior on every proxy route.

Download the official `NetToggle-0.9.0-beta.1.apk` below, verify SHA-256, install, authorize Shizuku and wait for readback. Before migrating from a differently signed Debug build, restore access for all affected apps and clear unwanted retained denies, then confirm uninstalling the old build before installing the official APK. Uninstalling does not clear rules. Actions Debug artifacts are not official release APKs.

## Integrity / 校验

- File / 文件：`NetToggle-0.9.0-beta.1.apk`
- Size / 大小：2,283,194 bytes
- APK SHA-256：`ef7b770c281b1988549e65ef4427502e02e7abdcc472129e2a1d2d677c46e5b2`
- Signing certificate SHA-256 / 证书指纹：`c72fdd1b4274c147a79dc2910ed5a187867eed42ad1aa19ca71fb9862d04961d`
- License / 许可证：[Apache-2.0](https://github.com/largebatata/NetToggle/blob/main/LICENSE)
