# Beta 公开核对报告

候选 `v0.9.0-beta.1`，versionName `0.9.0-beta.1`，versionCode `8`，Apache-2.0。计划仓库 `largebatata/NetToggle`，默认分支 main。

| 固定产物 | 值 |
|---|---|
| APK | NetToggle-0.9.0-beta.1.apk |
| 大小 | 2,283,194 字节 |
| SHA-256 | ef7b770c281b1988549e65ef4427502e02e7abdcc472129e2a1d2d677c46e5b2 |
| 证书 SHA-256 | c72fdd1b4274c147a79dc2910ed5a187867eed42ad1aa19ca71fb9862d04961d |
| applicationId | com.largebatata.nettoggle |

本轮重新核对原 APK 散列、官方签名验证、对齐及不可调试属性，均通过；不以新构建替换已人工验收文件。

本轮对公开源码副本重新执行离线干净构建：未签名 Release 构建成功，全部 72 项可用单测通过（失败 / 错误 / 跳过均为 0），Debug / Release Lint 均无问题。正式 APK 在 Pixel（Android 17）上的四项用户人工回归通过：授权与规则读取、GKD 禁止 / 恢复、Clash Meta 共存、重启后核实与手动恢复。此前 Debug Wi-Fi / 5G / Shizuku 停止及重连另记；这些不是自动化真机测试。

## 远程状态

文档和源码副本已完成隐私清理，排除密钥、SDK 配置、缓存、日志及私人设备记录；运行时代码与 APK 保持原样。提交后仍须核对公开历史和源码 ZIP。

已创建公开仓库 [largebatata/NetToggle](https://github.com/largebatata/NetToggle)，默认分支 main，识别为 Apache-2.0。首次提交：`cf4c3a044519dbb61bf86ced67af93514d618991`。

[首次 Actions](https://github.com/largebatata/NetToggle/actions/runs/39128766155)在 SDK 安装阶段失败，原因是 setup-android 默认安装已不可用的旧 `tools` 包。显式指定 `platform-tools` 后，[修复后的真实运行](https://github.com/largebatata/NetToggle/actions/runs/39128922942)全部成功，对应提交 `89e8742fc6ba60169314a8ca9fb90dcdc34234ee`。

下载 CI 报告核实：72 项单测全部通过，无失败、错误或跳过；Debug / Release Lint 均为零问题。Wrapper 校验、两个变体的干净构建、Debug 签名与对齐、测试产物检查 / 上传均通过。CI 只提供测试 Debug APK，未获取正式密钥。

Beta 草稿已创建，可由维护者在[仓库 Releases 页面](https://github.com/largebatata/NetToggle/releases)查看；最终交付报告提供最新草稿直达地址。接口核实为 Draft + Pre-release，发布时间为空；仅维护者可见。正式 APK 上传前、服务器记录和回下载的 SHA-256 均与固定产物一致。没有 Publish。

此报告的后续提交仅更新文档记录。交付前继续核对最终 main 的 CI，从最终 Git 快照导出源码 ZIP 并逐文件比较，再把草稿指向最终快照、核对全部资产。归档散列由草稿资产 `SHA256SUMS.txt` 提供，不在归档内记录其自身散列。最终提交与运行信息以最后的交付核对记录及仓库 Actions 为准。

## 发布判断

已成功核验的源码、CI、正式 APK 和用户人工测试支持小范围 Beta 试用；最终归档 / 草稿一致性仍须在交付时复核，**公开发布必须由所有者另行确认**。

CI 当前有非阻断警告：固定的部分 Actions 声明 Node.js 20，GitHub 实际强制用 Node.js 24 运行；setup-java v4 已弃用。本次运行成功，后续应单独维护 Action 版本并重验，不在本轮继续扩大变更。

单机人工测试不证明其他设备兼容。重启可能失效、共享 UID / appId / 多用户及内部接口限制详见 README；不为发布加入自动恢复、全局关闭链或后台监控。
