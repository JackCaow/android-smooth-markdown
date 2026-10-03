# Android Smooth Markdown 接入手册

适用于在自己的 Android 应用中接入 Markdown 阅读、流式回复、编辑器和样式定制。接入 UI 使用 Jetpack Compose；只需解析和 HTML 导出时可单独使用 JVM Core。

## 1 选择安装版本

截至 2026-10-01：

`0.5.0` 使用 Kotlin `1.9.24`、Compose Foundation `1.6.8` 和 compileSdk `35`；AAR 接入最低要求为 AGP `8.6.0`、compileSdk `35`，且不向宿主传递 Compose BOM。旧 `0.3.1` 发布包不包含此调整，仍需要 compileSdk 37；建议升级到 `0.5.0`。具体构建、消费者验证和自定义选择迁移见 [兼容性说明](android-compatibility.md)。

| 接入目标 | 使用方式 |
| --- | --- |
| 当前版本 | JitPack `0.5.0` |
| 固定源码构建 | 检出 tag `0.5.0`，构建到本地 Maven |
| Maven Central | 尚未发布，请使用上述方式 |

`0.5.0` 包含 Rust 解析器、独立 Core、分组配置、Design Token、后台调度、增量传输和完成时序修复。下面的公开 API 示例适用于 `0.5.0`；需要源码构建时采用第 10 节的固定 tag 方式。

项目地址：[android-smooth-markdown](https://github.com/JackCaow/android-smooth-markdown)。

## 2 安装已发布版本

要求 Android API 24+、compileSdk 35+、AGP 8.6.0+、Java 17 字节码和启用 Compose。源码使用 Kotlin `1.9.24` / Compose compiler `1.5.14`；独立消费者已验证 Kotlin `1.9.22` / compiler `1.5.8` 和 Kotlin `2.1.20` / 同版本 Compose plugin。Foundation 最低 `1.6.8`，宿主自行选择兼容 BOM；Gradle 运行 JDK 应匹配应用使用的 AGP/Gradle。

在应用项目的 `settings.gradle.kts` 合并以下仓库配置：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.JackCaow.android-smooth-markdown")
            }
        }
    }
}
```

在应用模块的 `build.gradle.kts` 添加：

```kotlin
dependencies {
    implementation("com.github.JackCaow.android-smooth-markdown:smoothmarkdown:0.5.0")
}
```

应用需启用 Compose 和匹配的 Compose compiler 插件。reader 会传递引入 Core、Compose Foundation 和协程，不需要再添加 Core。AAR 包含四种 ABI 的原生解析器，普通 Maven 接入不需要安装 Rust、Cargo 或 NDK。库的 manifest 已声明网络权限，应用最终合并 manifest 应保留 `android.permission.INTERNET`。

## 3 最小阅读页面

```kotlin
import androidx.compose.runtime.Composable
import com.jackcaow.smoothmarkdown.SmoothMarkdown

@Composable
fun MarkdownPage(source: String, openLink: (String) -> Unit) {
    SmoothMarkdown(
        markdown = source,
        selectable = true,
        onLinkClick = openLink,
    )
}
```

默认由 reader 自己纵向滚动。聊天气泡、`LazyColumn` item 或外层滚动容器内设置 `scrollable = false`，让外层负责纵向滚动。增强标题、引用、代码控制等装饰需显式设置 `useEnhancedComponents = true`。

新业务的配置较多时，使用分组 API：

```kotlin
import androidx.compose.runtime.Composable
import com.jackcaow.smoothmarkdown.*

@Composable
fun ChatMarkdown(source: String, openLink: (String) -> Unit) {
    SmoothMarkdown(
        markdown = source,
        renderOptions = MarkdownRenderOptions(
            scrollable = false,
            useEnhancedComponents = true,
        ),
        selectionOptions = MarkdownSelectionOptions(
            mode = MarkdownSelectionMode.DOCUMENT,
        ),
        events = MarkdownEvents(onLinkClick = openLink),
    )
}
```

## 4 统一样式和 Design Token

在业务主题层定义一份样式，传给 reader、stream 和编辑器预览。文档样式与宿主导航栏、聊天输入框的主题分别管理。

```kotlin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackcaow.smoothmarkdown.MarkdownStyleSheet

fun appMarkdownStyle(dark: Boolean): MarkdownStyleSheet {
    val base = MarkdownStyleSheet.github(dark = dark)
    return base.copy(
        blockSpacing = 12.dp,
        designTokens = base.designTokens.copy(
            typography = base.designTokens.typography.copy(
                paragraph = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            ),
            heading = base.designTokens.heading.copy(
                accentColor = Color(0xFF6750A4),
                decoratedThroughLevel = 0,
            ),
            details = base.designTokens.details.copy(cornerRadius = 12.dp),
        ),
    )
}
```

调用 reader 或 stream 时传 `styleSheet = appMarkdownStyle(dark)`。`decoratedThroughLevel = 0` 关闭增强标题的装饰条和分隔线。

| 配置 | 控制范围 |
| --- | --- |
| `designTokens.document` | 文档语义颜色 |
| `designTokens.typography` | 正文、六级标题字体和行高 |
| `heading`、`quote`、`code`、`link` | 增强组件装饰和代码控件 |
| `details`、`keyboard`、`math` | 折叠区、键帽和公式 |
| `plugins`、`mermaid` | 内置插件面板和图表基础样式 |
| `editor.MarkdownEditorTheme` | 编辑器工具栏、源码及编辑控件 |

使用嵌套 `copy()` 保留预设中的其他字段。显式 token 优先于旧样式字段；可选 token 设为 `null` 恢复回退。自定义 builder 输出由宿主自己应用样式。完整字段和优先级见 [样式手册](styling.md)。

## 5 流式回复

### 输入新增片段

```kotlin
import androidx.compose.runtime.Composable
import com.jackcaow.smoothmarkdown.StreamMarkdown
import kotlinx.coroutines.flow.Flow

@Composable
fun StreamingReply(
    chunks: Flow<String>,
    saveFinal: (String) -> Unit,
    reportError: (Throwable) -> Unit,
) {
    StreamMarkdown(
        chunks = chunks,
        throttleMillis = 50,
        scrollable = false,
        onComplete = saveFinal,
        onError = reportError,
    )
}
```

`chunks` 每次发送**新增文本**，例如 `"Hello "`、`"**world**"`。库会累积完整 Markdown。有限 Flow 正常结束后调用 `onComplete`；取消不等于正常结束。

### 输入完整快照

聊天列表可能回收气泡，建议由 ViewModel 持有每条消息的累计内容，再传入 `prefixes: StateFlow<String>`：

```kotlin
import androidx.compose.runtime.Composable
import com.jackcaow.smoothmarkdown.StreamMarkdown
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CumulativeReply(prefixes: StateFlow<String>) {
    StreamMarkdown(prefixes = prefixes, scrollable = false)
}
```

这里每次发送**完整前缀**，例如 `"Hello "`、`"Hello **world**"`。普通 StateFlow 不会正常结束，所以 `onComplete` 不会自动触发；业务持有完成状态，完成后可切换为静态 `SmoothMarkdown`。

在 ViewModel 中保存稳定的 Flow/StateFlow，避免在每次重组时创建新流。新增片段不要传入快照接口，完整快照也不要传入片段接口。

固定最新提交接入时，普通流式 Markdown 在后台解析、合并待处理的完整前缀；Markup 适配、插件回调和 UI 发布留在主线程。有限流完成回调等待最终可见布局，结束后释放原生会话和 worker 线程。自定义插件、含美元符号的公式路径、脚注、HTML 和 details 使用兼容解析路径；不能假设所有内容都走后台增量路径。

## 6 图片和中文标签

默认使用系统网络和图像 API，支持常见位图及受支持的 SVG。优先使用完整 HTTPS 图片地址。需要认证图片时，在当前账号的 reader 上配置资源参数：

```kotlin
import androidx.compose.runtime.Composable
import com.jackcaow.smoothmarkdown.*

@Composable
fun AuthenticatedArticle(source: String, imageToken: String) {
    SmoothMarkdown(
        markdown = source,
        renderOptions = MarkdownRenderOptions(useEnhancedComponents = true),
        resourceOptions = MarkdownResourceOptions(
            headers = mapOf("Authorization" to "Bearer $imageToken"),
        ),
        strings = MarkdownStrings(copy = "复制", copied = "已复制"),
        events = MarkdownEvents(onImageClick = { event ->
            println("图片：${event.source}，说明：${event.alt}")
        }),
    )
}
```

headers 会作用于该资源配置处理的图片请求，只给受信任内容使用账号凭据；需要按域名选取认证头时实现 `MarkdownResourceLoader`。loader 返回原始编码字节并遵守取消。`DEFAULT` 使用缓存、`RELOAD` 更新缓存、`NO_STORE` 不读写库缓存；自定义 loader 自己也应遵守缓存策略。

`MarkdownStrings` 只改变库的控件及无障碍标签，不翻译文章。全局可通过 `CompositionLocalProvider(LocalMarkdownResources provides resources, LocalMarkdownStrings provides strings)` 传入；单个 reader 的显式参数覆盖继承值。

## 7 插件和自定义渲染

只注册业务需要的扩展，并保持 registry 实例稳定：

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jackcaow.smoothmarkdown.*

@Composable
fun ExtendedArticle(source: String) {
    val plugins = remember {
        ParserPluginRegistry().apply {
            register(MentionPlugin())
            register(MermaidPlugin())
        }
    }
    SmoothMarkdown(markdown = source, plugins = plugins)
}
```

`ParserPluginRegistry` 扩展语法；`MarkdownBuilderRegistry` 替换已解析节点的 UI。分组 API 用 `MarkdownBuilders(nodes = …, image = …, code = …)` 传替换组件。registry 的注册、删除和清空会刷新 reader，应在 UI 线程进行。Mermaid 支持已实现的语法子集，不等同于完整 Mermaid.js。

## 8 编辑器

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorController
import com.jackcaow.smoothmarkdown.editor.MarkdownEditorMode
import com.jackcaow.smoothmarkdown.editor.SmoothMarkdownEditor

@Composable
fun MarkdownComposer(initialText: String, save: (String) -> Unit) {
    val controller = remember {
        MarkdownEditorController(initialText).apply {
            mode = MarkdownEditorMode.FORMATTED
        }
    }
    SmoothMarkdownEditor(controller = controller, onSave = save)
}
```

controller 默认 Source 模式；上例选择 Formatted，也支持 Preview/Split。`remember` 不会因 `initialText` 改变而重建 controller；切换到另一份文档时由业务显式切换或重建 controller。Markdown 源码是持久化内容。`styleSheet` 用于 Preview/Split 的 reader，编辑控件使用 `MarkdownEditorTheme`。

## 9 只接入解析 Core

使用第 2 节的 JitPack 仓库，仅添加：

```kotlin
implementation("com.github.JackCaow.android-smooth-markdown:smoothmarkdown-core:0.5.0")
```

```kotlin
import com.jackcaow.smoothmarkdown.MarkdownCoreParser

val parser = MarkdownCoreParser()
val ast = parser.parse("# Hello")
val html = parser.renderHtml("**Hello**")
```

Core 是 JVM JAR，使用 Java 17 和 Kotlin 标准库，不依赖 Android、Compose 或协程。没有可用 native backend 的 JVM 环境使用库自有 Kotlin 回退解析器。AST 导入路径是 `com.jackcaow.smoothmarkdown.ast`。

## 10 从固定版本源码构建

需要源码构建时，在开发机器检出固定 tag，再发布到**本地** Maven。该版本名只是本地版本，不表示 Maven Central 已发布：

```sh
git clone https://github.com/JackCaow/android-smooth-markdown.git
cd android-smooth-markdown
git checkout 0.5.0
./gradlew :smoothmarkdown-core:publishReleasePublicationToMavenLocal :smoothmarkdown:publishReleasePublicationToMavenLocal -PpublicationVersion=0.5.0-local
```

源码构建需要 SDK API 35、NDK 28+、Python、Rust 及四种 Android Rust target；具体安装见 [Rust 构建手册](rust-parser-build.md)。应用项目仓库添加：

```kotlin
mavenLocal {
    content { includeGroup("io.github.jackcaow") }
}
```

reader 依赖改为：

```kotlin
implementation("io.github.jackcaow:smooth-markdown:0.5.0-local")
```

不要同时保留稳定包的 reader 依赖。本地 Maven 只在构建机器可用；团队 CI 需构建同一固定提交并发布到团队 Maven 仓库，或在该 CI 的准备阶段完成本地发布。

## 11 常见问题和验收

| 现象 | 检查与处理 |
| --- | --- |
| 找不到依赖 | 确认 JitPack 仓库、完整模块坐标和版本 `0.5.0`；`0.3.0` 是失败的 JitPack 构建标签 |
| 找不到后台增量功能 | 升级到 `0.5.0`；第 10 节也提供固定源码构建方式 |
| 编译提示 Kotlin metadata 或 SDK 不兼容 | 核对 Kotlin/Compose compiler、compileSdk 及 Gradle/JDK，与第 2 节要求对齐 |
| 在 XML/View 工程使用 | reader 需要 Compose；通过宿主的 `ComposeView` 嵌入，或仅使用 Core 自己渲染 |
| 图片不显示 | 检查完整 HTTPS 地址、认证头、响应字节格式、网络权限及资源错误回调 |
| 滚动或高度冲突 | 外层负责滚动时 `scrollable = false`；检查父布局约束 |
| 流式文字重复 | 核对输入是新增片段还是完整快照，使用对应 overload |
| 完成回调不执行 | 检查是否使用不会结束的 StateFlow，以及上游是否正常完成 |
| 自定义字体、行距不一致 | 用 `designTokens.typography` 配置正文和标题，避免只改局部 Text |
| 图表或格式化编辑不支持某语法 | 查看 [实现范围](reference.md)，unsupported 情况按文档回退 |

接入后至少在业务页面确认：标题/正文/代码、表格和图片正常；浅深色及字号变化正确；聊天气泡没有双重纵向滚动；链接、复制、选择符合业务行为；流式最终文本与持久化源码一致；切换消息不会带回旧内容；完成和错误事件由宿主处理。

## 12 后续参考

- [公开配置契约](public-library-contract.md)
- [Design Token 和样式](styling.md)
- [API 和实现范围](reference.md)
- [后台流式验收](../benchmarks/stream-background-2026-10-01.md)
- [已发布版本](https://github.com/JackCaow/android-smooth-markdown/releases)

## 0.5.0 升级注意

新增 Highlight/Superscript/Subscript 可选插件和 GitGraph/Mindmap 原生图类型。升级后重新编译宿主；对 MermaidKind 的穷尽 when 需补新类型分支。布局和 token 构造的已有源码调用保留，但 data class 的 JVM constructor/copy 签名发生变化。
