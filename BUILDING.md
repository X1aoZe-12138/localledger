# 构建说明

需要 JDK 17、Android SDK 35（含 Build Tools）和项目自带的 Gradle Wrapper。首次运行 Gradle 需要下载依赖。

## Debug 构建

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

公开源码没有作者的本地签名文件时，Gradle 使用常规本机 debug 密钥。不同电脑生成的 debug APK 签名不同，通常无法直接覆盖彼此安装，升级前应先备份数据。

## Release 构建

release 使用独立包名 `cn.localledger.community`，需要你自己创建并长期保管的签名密钥。为当前 PowerShell 构建进程设置：

```text
LOCALLEDGER_RELEASE_STORE_FILE
LOCALLEDGER_RELEASE_STORE_PASSWORD
LOCALLEDGER_RELEASE_KEY_ALIAS
LOCALLEDGER_RELEASE_KEY_PASSWORD
```

然后执行：

```powershell
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintRelease assembleRelease
```

缺少任一签名参数时，release 打包会失败，避免误发未签名 APK。产物在 `app/build/outputs/apk/release/app-release_0_9_2_beta_1.apk`。完整 Compose UI 单元测试在 debug 变体运行；release 变体运行其余单元测试。

**把签名密钥和密码在仓库之外分别安全备份，绝不要提交到 GitHub、附在 Release、写入 Issue 或聊天记录。** 后续同包名 APK 必须使用相同证书签名才能覆盖安装并保留原有数据。只有 APK 而没有签名私钥，无法制作可覆盖安装的更新。

源码中的 `.tools/signing/local-ledger-debug.keystore` 路径仅用于原开发环境保留早期个人 debug 安装的签名；公开源码不含该文件，也不需要它即可构建普通 debug 包。
