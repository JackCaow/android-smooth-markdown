# Android 兼容基线

本次调整只涉及 Android。既有 `0.3.1` 发布 tag 不变，仍需要 compileSdk 37；以下较低基线适用于本次调整后的源码和未来包含它的发布包。

## 构建库与接入库

| 项目 | 库源码构建 | 接入打包 AAR 的应用 |
| --- | --- | --- |
| Kotlin | 1.9.24 | 已验证 1.9.22、2.1.20 |
| Compose | Foundation 1.6.8 / Material3 1.2.1 | Foundation 1.6.8 起，由宿主选择 BOM |
| Compose compiler | 1.5.14 | 匹配宿主 Kotlin；1.9.22 使用 1.5.8，2.1.20 使用同版本 Compose plugin |
| compileSdk | 35 | 35 起 |
| AGP | 8.9.1 | AAR 声明 8.6.0 起；独立项目验证 8.6.1 |
| Gradle | 8.11.1 wrapper | 匹配宿主 AGP |
| JVM 字节码 | Java 17 | Java 17 |
| 最低运行 Android | API 24 | API 24 |
| Rust / NDK | 源码构建需要 | 不需要，四种 ABI 已打包 |

库不再导出 Compose BOM，避免要求宿主同步升级整套 Compose。Foundation 与 Material3 显式声明最低依赖，宿主使用更高版本时仍按正常 Gradle 依赖解析规则决定最终版本。这不意味着任意更高版本均已实测。

对已发布的 `0.3.1`，修改应用自己的 Kotlin 或 BOM 不会消除旧 AAR 的 SDK 37 要求。需要使用包含本次调整的源码/新发布包。

## 选择功能迁移

原有 reader、stream、editor、`SmoothSelectionController` 公开签名保留。文字、代码、表格、RTL、非文本桥接、全文语义复制、原生菜单及自定义动作由库自己的选择层实现，避免依赖 Compose 1.12 才提供的 `SelectionState`。

外部 `NATIVE_TEXT` builder 直接输出任意 Compose `Text` 时，仍支持原生局部选择。要参与控制器的跨块选择，改用 `SmoothSelectableText`，或通过 `context.renderChild` / `context.renderInlineChildren` 渲染已有 AST 子节点：

```kotlin
import androidx.compose.runtime.Composable
import com.jackcaow.smoothmarkdown.*
import com.jackcaow.smoothmarkdown.ast.Node

class LabelBuilder : MarkdownNodeBuilder {
    override fun canBuild(node: Node) = true // 注册到需要替换的特定节点类型
    override fun documentText(node: Node) = "自定义文字"
    override fun selectionMode(node: Node) = MarkdownBlockSelectionMode.NATIVE_TEXT

    @Composable
    override fun Render(node: Node, context: MarkdownBuilderContext) {
        SmoothSelectableText("自定义文字", style = context.styleSheet.paragraphStyle
            ?: androidx.compose.material3.LocalTextStyle.current)
    }
}
```

`SmoothSelectableText` 提供 String 和 AnnotatedString 重载，默认采用宿主 LocalTextStyle，也接受 Modifier 和 TextStyle。它应保持与 `documentText` 相同的逻辑文字顺序。全文模式原有的未知/不支持 builder 校验仍适用。

选区颜色继续采用宿主 `LocalTextSelectionColors`。本次不要求宿主使用库 Demo 的主题、导航或聊天界面。

## 独立消费者验收

`compatibility-consumer` 是独立 Gradle 构建，使用发布后的 Maven 坐标，而不是同工程的 project 依赖。它分别验证旧/新 Kotlin 编译器下的 reader、分组配置、stream、editor、选择与自定义 builder。

```bash
./gradlew :smoothmarkdown-core:publishReleasePublicationToCentralBundleRepository \
  :smoothmarkdown:publishReleasePublicationToCentralBundleRepository

./gradlew -p compatibility-consumer clean assembleDebug \
  -PartifactRepository="$PWD/build/central-staging" -PartifactVersion=0.3.1

./gradlew -p compatibility-consumer clean assembleDebug -PmodernKotlin=true \
  -PartifactRepository="$PWD/build/central-staging" -PartifactVersion=0.3.1

python3 tools/check_consumer_requirements.py
python3 tools/check_public_api.py
```

这里的版本号仅标识本地测试产物，不会覆盖远程已发布 tag，也不是 Maven Central 已发布的证明。正式发版后应使用新版本号，并在独立消费者上复验远程下载产物。

工具同时检查 AAR 最低 SDK/AGP、POM 不导出 Compose BOM、JVM 方法签名和四种 Rust JNI ABI。UI 验收覆盖 `PublicLibraryUiSuite`、`SelectionCompatibilityUiSuite` 和 Demo `ConversationLongPressUiTest`；CI 固定使用 API 35 模拟器。

## 本次验收记录（2026-10-01）

- Gradle 8.11.1 wrapper 完成库、Core、Demo、测试 APK 和本地 Maven 发布构建。
- 主机测试共 538 项：534 通过，4 项既有测试跳过，0 失败。Core 17 项、reader 498 项、Demo 23 项。
- API 35 arm64 模拟器：PublicLibraryUiSuite 与 SelectionCompatibilityUiSuite **56/56 通过**，包括实际点击 Android 原生 Copy 菜单、全文/段落/跨块选择、表格及 RTL 顺序、拖动、非文本桥接、自定义文字条件重组和流式完成时序。
- 同一模拟器 Demo ConversationLongPressUiTest **2/2 通过**。Demo 的 targetSdk 仍为 35。
- 独立 Maven 消费者：Kotlin 1.9.22 / Compose compiler 1.5.8 与 Kotlin 2.1.20 / 同版本 Compose plugin 均在 AGP 8.6.1、compileSdk 35 下完成 clean assembleDebug。两者均只使用暂存 Maven 产物，不使用库 project 依赖。
- 公开 JVM 签名检查、AAR SDK/AGP 元数据、POM 无 Compose BOM、四种 JNI ABI 及 16 KB ELF 对齐检查通过。
- 八组 Android Demo 数据与 Flutter 固定样例一致。

旧 Compose 1.6 的测试清单采用默认带 ActionBar 的 Activity；测试 APK 使用 targetSdk 34，避免 Android 15 强制窗口布局使其测试内容不可见。实际运行系统仍是 API 35；这不是库的最低 SDK、编译 SDK 或宿主 targetSdk 限制。引用背景的绿色及更新后的黄色均以实际像素复验，原有精确颜色断言保留。

此次为源码兼容性调整的本地验收，未包含真机验收或新版本远程发布验收。
