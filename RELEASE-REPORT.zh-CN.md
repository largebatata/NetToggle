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

已确认 GitHub 登录账号为 largebatata，目标仓库尚不存在。仓库创建、推送、首次 Commit SHA、Actions 真实运行、Release 草稿 URL 仍待远程执行和核验，不记作成功。

后续须推送审核后的 main，记录首次 / 最终 SHA；从最终 Git 快照导出并校验源码 ZIP；检查 Actions 地址、提交和最终结果，失败则最小修复重验；再次核对原 APK 后创建 Draft + Pre-release 并核验上传资产。不得 Publish。

## 发布判断

当前远程流程尚未完成，不满足公开 Beta Release 的全部条件；真实 CI、草稿与资产必须核验，最终公开还须所有者确认。单机人工测试不证明其他设备兼容。重启可能失效、共享 UID / appId / 多用户及内部接口限制详见 README；不为发布加入自动恢复、全局关闭链或后台监控。
