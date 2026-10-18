# 本地记账 LocalLedger

一个在 Android 手机上离线运行的记账 App。得到通知使用权后，它会尝试从微信、支付宝的**支付通知**中记录金额；没有捕获到的账单也可以手动添加。账本只保存在手机里。

当前公开测试版：**0.9.2-beta.1**，支持 Android 12 及以上。测试版的应用包名是 `cn.localledger.community`。

## 下载与使用

在 GitHub 仓库的 **Releases** 页面下载 `app-release_0_9_2_beta_1.apk`，用手机打开并安装。页面上自动生成的 `Source code.zip` 是源码，**不是安装包**。首次使用时，在 App 设置里开启「通知使用权」；Android 13 及以上如果想收到本 App 的提醒，还需允许通知权限。只做手动记账时无需开启这两项权限。

可以手动记账、编辑和分类账单，使用商户快捷项、AA 分摊、个人支出统计、资产快照，以及 JSON 备份与恢复。通知识别依赖支付 App 实际发出的文本，可能漏记或误记；请以支付平台的正式账单为准，定期核对。

## 数据与隐私

- App 不申请 `INTERNET` 权限，不含账号、广告或遥测。账本和主动开启的通知诊断日志保存在设备本地，Android 自动备份已关闭。
- 「通知使用权」用于读取系统通知；App 只尝试解析微信和支付宝的支付通知。主动开启诊断模式后，会临时记录**所有 App** 的通知，可能包含私人内容，请在使用后清空。
- 主动导出的 JSON 备份、CSV 和财务报告可能包含个人财务信息。备份文件未经加密，务必自行妥善保管，不要上传到公开仓库或 Issue。
- 更换手机时，在旧手机使用「设置 → 数据管理 → 备份全部数据」保存 JSON；在新手机安装 App 后使用「恢复备份」。恢复会先校验，再经确认**替换新手机当前账本**。CSV 仅供查看，不是完整备份。

## 从源码构建

源码使用 Kotlin、Jetpack Compose、Room 和 Gradle。需要 JDK 17、Android SDK 35；电脑首次下载构建依赖时需要联网，安装后的 App 不需要联网。Windows 上可运行：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

普通 debug APK 位于 `app/build/outputs/apk/debug/`。自己发布可供别人覆盖安装的 release APK 时，需要**自己保管的长期签名密钥**；构建细节见 [BUILDING.md](BUILDING.md)。请勿将密钥、密码、个人账本、诊断日志或 `local.properties` 放进公开仓库。

## 许可证与来源

项目需求来自维护者的实际使用；开发过程中使用 OpenAI Codex 辅助设计、编写代码、运行测试和整理文档。维护者负责真机验证、发布和后续维护。

本项目以 [Apache License 2.0](LICENSE) 开源。开发时调研过其他公开 Android 记账项目；具体项目和第三方组件说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。本项目与微信、支付宝及所列参考项目无隶属或官方合作关系，支付通知格式也不由本项目控制。开源许可和免责声明不能替代正确的来源标注；若发现遗漏的第三方材料，请提出 Issue 以便核查和补充。

当前限制见 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)，主要设计决定见 [DECISIONS.md](DECISIONS.md)。
