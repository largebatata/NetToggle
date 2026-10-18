# 正式签名与更新

版本 `0.9.0-beta.1`，versionCode `8`，applicationId `com.largebatata.nettoggle`。

- 独立 NetToggle 正式证书；PKCS12 alias：`nettoggle-release`。
- RSA 4096 / SHA256withRSA；证书有效期 2026-10-18 至 2054-03-05。
- 证书 SHA-256：`c72fdd1b4274c147a79dc2910ed5a187867eed42ad1aa19ca71fb9862d04961d`。
- APK：`NetToggle-0.9.0-beta.1.apk`，2,283,194 字节。
- APK SHA-256：`ef7b770c281b1988549e65ef4427502e02e7abdcc472129e2a1d2d677c46e5b2`。

正式证书与 Debug 不同，没有复用其他项目密钥。两份加密容器位于源码外不同物理磁盘，已核对一致且均能读取同一证书。公开材料不提供私钥、密码、本地目录或备份设备信息。

## 构建边界

Gradle 不含正式 signingConfig、密钥路径或密码。未签名 Release 脚本执行构建、全部可用单测、Debug / Release Lint；正式签名在本地用 Android 官方工具完成，对齐在签名前完成，签后核验签名、证书、对齐和不可调试属性。未签名中间文件不可作为正式安装包。

CI 不接收正式密钥 Secrets，不使用 Debug 证书生成官方 Release，不自动发布。源码排除签名材料、本地 SDK 配置、缓存和报告。

## 维护者恢复原则

保留独立加密备份及不含密码的恢复说明，密码由所有者单独安全保存。恢复到项目外私有目录，核对私有备份记录，通过安全交互读取 alias，再核对公开证书指纹。不将密码写入命令行、脚本、Gradle、日志或明文配置。

后续更新必须沿用同一证书并递增 versionCode。同名 alias 不等于同一应用身份，不可重新生成密钥替代原文件。更改容器密码后须重新验证备份。

## Debug 迁移

不同签名通常无法覆盖安装。先恢复全部被禁止应用，核实实际请求并清除不需要的保留 deny，再由用户确认卸载旧版并安装正式版。停止 Shizuku、卸载或清数据不会主动清理规则。不得为处理签名冲突自动卸载、清数据或全局关闭 Chain 3。
