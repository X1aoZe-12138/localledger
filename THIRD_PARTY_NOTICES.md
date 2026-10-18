# 来源与第三方说明

开发早期调研过以下公开项目的功能、通知监听思路和 Android 适配经验：

- [DykiSensei/seamless-bookkeeping](https://github.com/DykiSensei/seamless-bookkeeping)（MIT）
- [code-runner-xx/autoledger](https://github.com/code-runner-xx/autoledger)（调研时未发现明确的仓库开源许可证）
- [FridayKoi/WhereIsMyMoney](https://github.com/FridayKoi/WhereIsMyMoney)（Apache-2.0）

本仓库的应用业务代码是针对本项目需求编写的；未有意识地从上述项目复制源码、资源或测试数据。列出参考项目是为了透明说明调研来源，**不表示它们为本项目背书**。如发现某段具体内容需要补充著作权声明或许可文本，请提出 Issue 并指出文件与行号，我们会核查并更正。没有明确许可证的公开仓库不能因为“代码可见”就直接复制使用。

`gradlew`、`gradlew.bat` 与 `gradle/wrapper/gradle-wrapper.jar` 使用 [Gradle 8.11.1](https://github.com/gradle/gradle/tree/v8.11.1) 的 Wrapper，Gradle, Inc.，Apache License 2.0；许可文本见 `licenses/Apache-2.0.txt`。

应用依赖 Kotlin、AndroidX Compose/Room/Lifecycle/Core/Activity 与 Kotlin Coroutines，通过 Gradle 仓库解析；测试使用 JUnit、Robolectric 等开发依赖。第三方库由各自权利人依其许可证授权。Android SDK 与 JDK 是构建工具，不作为本项目源码发布。
