# NetToggle

[English](README.en.md) · [Apache-2.0](LICENSE) · **0.9.0-beta.1**（versionCode 8）

免费、开源、轻量级的 Android 应用联网开关。通过 Shizuku UserService 读取和设置 Android Connectivity Chain 3 规则，使用原生 Views，支持系统动态配色和深色模式，界面以中文为主。

只提供应用联网开关、真实系统规则读取、Shizuku 连接和系统流量统计页面入口。**无需 Root，不占用 VPN，不申请 INTERNET 权限，不采集流量，不使用常驻后台监控服务。** 不提供流量统计、DNS 过滤、广告过滤或复杂防火墙规则。

## 兼容性与实测范围

- 最低安装版本 Android 13 / API 33；compile / target SDK 37（Android 17）。最低 SDK 不代表所有 Android 13+ 设备均可正常控制联网。
- **目前仅在 Pixel（Android 17）上完成主要真机测试。其他 Android 设备和系统版本尚未完成兼容性验证。**
- 系统内部 Connectivity 命令依赖固件。NetToggle 会尝试读取实际规则，但不能保证所有固件接口兼容；无法识别的结果显示未知，不能执行开关。
- 仅支持主用户 user 0；不显示系统应用、自身和低 UID 应用。

以下为用户提供的人工真机结果，与自动化单测、构建和 Lint 分开记录：

| 正式签名 0.9.0-beta.1 回归 | 用户报告 |
|---|---|
| Shizuku 授权及系统规则读取 | 通过 |
| GKD 禁止联网和恢复联网 | 通过 |
| Clash Meta 共存 | 通过 |
| 重启后的真实状态核实与手动规则恢复 | 通过 |

此前 Debug 版本另通过 Wi-Fi、5G、Shizuku 停止 / 重连等人工测试；Wi-Fi + Clash Meta 中 GKD 被禁止后浏览器仍联网，Clash 保持连接。结果仅覆盖所测设备和场景，不能据此承诺所有设备、移动数据 + Clash、代理路由或共享 UID 组合均兼容。详见[测试说明](TESTING.zh-CN.md)。

## 安装与授权

1. 从 [Shizuku 官方下载页](https://shizuku.rikka.app/download/)安装 Shizuku，按[官方手册](https://shizuku.rikka.app/zh-hans/guide/setup/)使用无线调试或电脑 ADB 启动。无需 Root；使用当前 Shizuku 界面给出的启动命令。
2. 从本仓库已发布的 Release 获取 `NetToggle-0.9.0-beta.1.apk`，核对 SHA-256 后在主用户安装；如果版本仍为草稿，请等待发布。Actions Debug 产物不是官方安装包。
3. 打开 NetToggle，点 Shizuku 标签并授权。只有当前权限有效且 UserService 存活，才显示“Shizuku 已授权”；另有未授权、未启动、连接中和服务断开提示。
4. 等待规则核实，先对非关键应用测试，并保留一个可联网的对照应用。

APK SHA-256：

```text
ef7b770c281b1988549e65ef4427502e02e7abdcc472129e2a1d2d677c46e5b2
```

正式证书指纹见 [SIGNING.md](SIGNING.md)。Debug 与正式签名不同，通常不能覆盖安装：迁移前先确认所有被禁止应用已恢复真实联网，并清除不需要的保留 deny，再由用户确认卸载旧版并安装正式版。卸载或清数据不会主动清理系统规则。

## 开关、分组与快照

**开关开启 = 允许联网；关闭 = 禁止联网。** 修改结果须由系统读回确认；失败明确报错并重新核实必要范围，不乐观显示成功。禁止和关键应用操作保留风险确认。

| 系统读数 | 显示和分组 |
|---|---|
| Chain 3 开启、应用 deny | 已禁止；开关关闭；全部列表排最前 |
| 应用 allow | 未禁止；开关开启 |
| Chain 3 关闭、deny 仍保留 | 当前未禁止；开关开启；提示保留 deny，可点状态清除 |
| 历史、未读取或失败 | 只在全部；标明历史或未知；不可操作 |

分组和计数只依据核实结果，组内按名称排序，搜索只作用于当前组。核实是最近一次成功的系统规则快照，不是持续联网探测。

历史使用私有 SharedPreferences，**不是 DataStore，也不是自动恢复设置**。冷启动先显示标明为历史的缓存，异步优先核对上次禁止项，每批最多 8 项；缓存不能成为实时结果或解锁开关。

同进程短暂返回、分组、搜索和滚动复用已核实快照。冷启动、手动刷新、Shizuku 重连、清单变化或后台离开满 10 分钟后返回才全量核实；前台没有轮询。单个开关只补读受影响 UID 和可能生效的保留 deny。其他工具改规则后请手动刷新。

## 重启与安全恢复

**Android 重启后封锁规则可能失效，可能需要重新启动 Shizuku。** 用户观察到 GKD 重启后恢复联网，但尚未确认是 Chain 3 关闭、具体规则清除，还是两者同时发生。NetToggle 无开机常驻、不自动恢复禁止，也不全局关闭 Chain 3。

1. 重新启动 Shizuku，连接 / 授权 NetToggle，等待读取实际规则。
2. 历史禁止记录不表示当前仍被封锁。若仍需禁止，核实后手动关闭开关，再用新请求验证。
3. 恢复联网时打开开关，等待允许读回；链关闭且 deny 保留时可点状态清除。
4. 失败 / 超时表示结果未确认，先刷新再决定，不反复点击或全局关闭链。停止 Shizuku、卸载 NetToggle 不会主动撤销规则；需要时重新启动 Shizuku，用 NetToggle 恢复目标应用。

## 安全边界

- 规则按 appId 生效，共享 UID、同一 appId 和多用户可能联动。只支持主用户和隐藏系统应用不能消除间接影响。**不建议对关键系统应用使用联网封锁。** 对 Clash、Shizuku 等关键应用也应谨慎。
- 禁止可能启用全局 Chain 3，激活其他工具保留的 deny。恢复只清除目标规则，不全局关闭链。
- 不占用 VPN，不改 Clash 配置；共存结果只覆盖所测场景。系统读数不保证所有代理路径、系统代发请求和端到端效果。
- 不添加开机接收器或常驻 Android Service。有限读写可能在切出页面后完成；Shizuku 非 daemon Binder UserService 不承担后台监控。
- 私有历史排除备份，无流量、请求和日志采集；列表复用行 / 图标，规则 I/O 在后台线程执行。

## 构建

使用 JDK 21、SDK 37、Build Tools 36.0.0；固定 AGP 9.3.2、Gradle Wrapper 9.5.0、Shizuku API / Provider 13.1.5。Java / Kotlin 输出目标 17，Wrapper 发行包固定 SHA-256。

PowerShell 7（兼容 7.6.6，以下路径为通用示例）：

```powershell
$env:ANDROID_HOME = 'C:\AndroidSdk'
$env:JAVA_HOME = 'C:\Java\jdk-21'
.\Build-Debug.ps1
# 已缓存全部依赖时可加 -Offline
```

脚本复制源码到 ASCII TEMP 目录以兼容 Windows AIDL，干净构建、单测及 Debug Lint 的报告 / 测试 APK 写入忽略目录 `artifacts/`，不改全局代理或手机。Linux / ASCII 路径可直接运行：

```bash
bash gradlew --no-daemon --console=plain clean :app:test :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```

`Build-Release-Unsigned.ps1` 生成不可调试的**未签名** Release，运行全部可用单测及 Debug / Release Lint；未签名输出不能作为正式安装交付。源码没有正式签名密钥或密码。

[Actions](.github/workflows/android.yml) 使用固定提交的第三方 Actions、只读仓库权限，验证 Wrapper，构建 / 测试 / Lint 后检查 Debug APK 签名和对齐。仅上传明确标记的测试 Debug APK 和报告，不接收正式 keystore，也不签名或发布官方 Release。实际结果见[发布核对报告](RELEASE-REPORT.zh-CN.md)。

## 许可证

[Apache-2.0](LICENSE)，第三方依赖保留各自许可，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。[公开前审查](AUDIT.zh-CN.md)记录源码和隐私边界。不要提交私钥、密码、本地 SDK 配置、构建缓存或个人设备记录。
