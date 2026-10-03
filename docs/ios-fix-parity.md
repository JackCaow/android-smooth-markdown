# iOS → Android 迁移修复核对

## 范围与基线

- iOS `origin/main`：`3de6470`（公开 0.4.0），按实际 `git show` 的改动核对。
- Android 基线：`6b4f3e3`（公开 0.4.1）；本文件与修复位于本次隔离工作区。
- 消费者：DimRemote `4fc72fe9c1`。原始迁移为 iOS `000d8a04cd`、Android `4b98c580e9`，后续实际提交单列。
- “已有等价”指已核对代码实现，不代表所有设备场景已验收；源码风险与设备复现不能混为一谈。
- 不按两端版本号比较；Android 0.4.1 的发布时间早于 iOS 0.3.5/0.4.0 修复。

## 迁移后 iOS 库逐提交矩阵

| iOS 提交 | 问题/目的 | Android 0.4.1 状态及证据 | 本次处理 |
|---|---|---|---|
| `5e89da7` | `\\(…\\)` / `\\[…\\]` native projection | `becb762`/NativeMarkdownParser、Core native extensions 已有 | 保留 shared 实现 |
| `28d1332` | 主流代码高亮和 Mermaid fallback 本地化 | `58497f2`/CodeSyntaxHighlighter、MarkdownStrings 已有 | 保留 |
| `a72fae3` | 私有 SFUI 字体名导致加载失败 | Android Compose 使用系统 FontFamily，不涉及 UIFont 私有名字 | 平台专属，Android 无对应缺陷 |
| `2cf314b` | math literal/转义/code优先级、局部字体scale、代码 literal 分类 | `a714719`、`d7fa2d0` / shared Rust `block.rs`；Compose LocalDensity 字体随宿主环境 | 核对已有；不重复换 parser |
| `3b45bc8` | streamed nested brackets/array math | `530d35c` / Rust backslash math 回归 | 已有等价 |
| `2c9192a` | cache 精确 Unicode 源码身份 | MarkdownParseCache.kt 使用 Kotlin String 作 key，String equality 逐 UTF-16，没有 Swift canonical equivalence | 无需移植 Swift `[UInt16]` workaround，添加 Unicode cache 回归 |
| `2c9192a` | 多位 ordered marker 在固定窄宽度下换行 | SmoothMarkdown.kt marker 固定 `width(listIndent)`，确认缺失 | 改 `widthIn(min = listIndent)`，宽数字自然扩展 |
| `2885597` | 嵌套 lazy 非滚动 reader 初帧未完整量测 | Android `scrollable=false` 分支使用 eager Column；默认外层 RecyclerView wrap-content | 已有等价；不添加固定最小高度 |
| `2e76a1a` | UIKit 非滚动 intrinsic-height 缓存过期 | Android 无 UIViewRepresentable / TextKit intrinsic cache；Compose 量测重算 | iOS 平台专属；Android 单独检查动态scrollbar占位 |
| `28681d8` | `==highlight==` / `^sup^` / `H~2~O` 可选插件 | Android 只有 HTML mark/sub/sup，没有三种 opt-in Markdown 插件 | 新增 InlineFormattingPlugins.kt，保留默认 CommonMark/GFM 与 code/escape 边界 |
| `28681d8` | 插件格式融合周围 bold/link 和 sheet | Android AnnotatedString 父节点 marks 原本能覆盖子文本，新增插件必须采用当前 sheet | inlineRender 为新节点选择当前 highlight/sub/sup 样式，custom builders 仍优先 |
| `28681d8` | `<small>` 和可配置 smallStyle | Android HTML tag 当普通透明标签，无字号缩放 | 增加 smallStyle 和 80% 相对字号；保留周围 marks |
| `28681d8` | 同行 details / paragraph/list 内 disclosure / trailing text | NativeMarkdownParser 仅认整行 `<details>` opener；缺失 | HTML opt-in postprocessor 保留 source/code literal，拆分到原容器下，递归处理 nested disclosure |
| `28681d8` | disclosure 中 unmatched backtick 不应吞 closer | 旧 Android 同行根本未解析 | 同步 exact-run paired-backtick scanner；unmatched backtick 当文字 |
| `28681d8` | HTML code substitution 保留原 source range | HtmlCodePostProcessor 替换成 Code 但未赋 sourceSpans | 保留完整 HTML span 的原始范围，添加回归 |
| `38d80d8` | Mermaid scroll viewport 裁剪避免覆盖标题/后文 | Android Mermaid 原版无对应 clipping | Mermaid 任务修复，见其测试与变更 |
| `8a209a3` | native Git/mindmap、pie inline title、Unicode/space sequence、self-edge | Android 公共 AAR parser probe 已确认缺失 | Mermaid parser 任务同步 |
| `8a209a3` | curved routes/ports/arrowhead/label/ER bounds/text measurement | Android 公共 AAR + native source 审计确认缺失 | Mermaid layout 任务同步 |
| `5e7236e` | Mermaid border scroll 裁剪、公开 tokens | Android 缺 border/marker/curve 的可配置 tokens | Mermaid layout 任务同步 |
| `6b8ceb9` | Git/mindmap CRLF header | Android 尚无对应 diagram kind | Mermaid parser 任务同步 |
| `3de6470`, `b167e8a`, `22ee68c`, `ca8a2b7`, `b3b51ab` | CocoaPods 发布和安装文档 | Android 有自己的 JitPack coordinate，不能套 Pod 文档 | 不属于渲染修复；发布验收分开 |

## 迁移前基础修复的回溯核对

| iOS 历史修复 | Android 对应/差异 | 核对结论 |
|---|---|---|
| `5458cd7` reader 行距和 decoration layout | Compose TextStyle/paragraphStyle、MarkdownStyleNormalization，文字按 LocalDensity 量测 | 已有 Android 实现；设备首帧/大字体仍需视觉验收 |
| `b7ff5e8` DeepSeek stream / inline code | Android `956e86f` DeepSeek demo，inline code 是 AnnotatedString background，无独立错误 baseline widget | 有等价实现；key 必须继续本地开发注入 |
| `e83eaff` HTML code verbatim | Android `e3f5fad`/HtmlCodeVerbatimTest | 已有；本次补 sourceSpans |
| `0221548`, `1f00d97` nested details/fenced tags | NativeMarkdownParser details depth/fence tracking + DetailsTest | 已有多行支持；本次补同行缺口 |
| `42c9578`, `fd21cf6`, `9433c00`, `dab4994` 图片/SVG 内容探测、比例与选区 | NativeImageLoader + `a2bf70e` decoded dimensions；Android WebView/System SVG，与 iOS TextKit 完全不同 | 保留平台实现，消费者图片实际尺寸完成更新另核对 |
| `bd1337e` cases MathML brace height | Android NativeTeX renderer 不走 iOS MathML WebKit 路径 | 平台专属；公式现有 native metrics 回归仍执行 |
| `7d3e2c4` public API/design tokens/parser Core | Android `153513e` | 已有等价，新增small/Mermaid token继续公开配置 |
| `e0c9eed` shared Rust C ABI | Android `8e64c29` JNI | 同一个 owned Rust AST，平台包装不同 |
| `6d82185` Rust editor plugins/export | Android `85d00c7` | 已有共同源码，不复制 iOS 编辑器包装 |
| `22282e7` stable stream blocks/incremental parser | Android `0960e17` | 已有 shared incremental session |
| `043093b` background coalescing/latest final layout | Android `71173df` StreamingMarkdownWorker | 已有后台 coalescing / final publish，非 UI 线程解析 |
| `9ffe93b` GFM table column finite viewport/alignment | Android `766d871` + SmoothMarkdown.kt MarkdownTable `IntrinsicSize.Min` | 库已有；消费者自定义 builder 尚有行高缺口 |
| `0e7c9fa`, `8934e2d`, `c680594`, `7a6986b` reader/editor native selection | Android `47e7bef`/`d306609`/`eb8d027`/`8448b5d` | 两端不同 selection 引擎，具备对应能力；不复制 TextKit实现 |
| `657b718` stream/demo layout | Android `0b4aceb` migration integration | 已有对等整合；本次仅修真实确认的库缺口 |
| `223e867`, `32f2d57`, `e97307c` demo nav/subtitle/drawer | Android `fd8e3db`, `09e1dd4`, `2707604` | 已有 Android demo 独立对应提交 |
| `db55b07` Flutter reader/stream API | Android `536c4bd` | 已有 API parity；签名基线需新增插件后刷新 |

## 消费者迁移后逐提交核对（不是库补丁）

| DimRemote 提交 | 修复/证据 | Android 状态/后续 |
|---|---|---|
| `e008845419` | exact source + host interaction | Android MarkdownText.kt 传原文/host theme/events，缓存以源文本区分；保留稳定 row 边界 |
| `c17ba46beb` | Android copy/table accessibility | Android 专属已有，不反向复制到 iOS |
| `04170fa98e` | pin public Android 0.4.1 | 已安装 QA 仍使用公开 0.4.1，未含本次库修复 |
| `35de3a715d` | iOS inline assistant thinking → work rows | 已补 ChatViewModel assistant Text → ThinkingMessage 显示投影，fence/stream tail/stable IDs/role guards 与 iOS 对齐，原 RCP 源文不变；88 项投影流程测试通过 |
| `c1f6a2634e`, `1b18bea422`, `1efaea7d25` | first mount/recycle/table/image host regression | Android RecyclerView 会更新 ComposeView + wrap-content，无 iOS intrinsic cache；要以真实手机首帧/替换截图验收 |
| `f4c831188e` | iOS table保留多列、图片加载尺寸完成后settle | Android RemoteMarkdownBuilders 的 table Row 未 IntrinsicSize.Min、grid cells 未 fillMaxHeight，是消费者独立缺口；交 root 修复 |
| `cc2091c93f`, `4fc72fe9c1` | iOS diagram review/public0.4.0/device acceptance | Android 没有这批 Mermaid 修复；本次库同步后需重新接入与真机验收 |

## Android 独立发现的直接回归

- CodeBlocks.kt 原先在 `horizontalState.maxValue > 0` 后才加入 scrollbar，占位会在第一帧量测后增加。现在 `showScrollbar` 时先保留同样位置，仅绘制依赖最终 scroll extent，避免动态改变块高。
- 此问题是 Android 独立的首帧高度风险，不能表述为已复现 iOS 的 TextKit 缓存 bug。

## 验证状态

- 针对性 JVM 测试：DetailsTest、HtmlCodeVerbatimTest、MarkdownParseCacheTest、InlineFormattingParityTest，24 项通过，日志 `/private/tmp/android-nonmermaid-parity-test.log`（2026-10-03）。
- Mermaid 的 parser/layout 测试由并行任务补充。
- Android 真机实际录屏/截图不由单元测试替代；历史仪器测试 Activity 启动失败，不计为通过。
- 当前补丁不等于公开发布或消费者安装已升级；最终合并、版本、公开AAR和手机接入分别验收。

- 消费者 thinking 投影：MessageTimelineProjectionTest 21 项、ChatViewModelProjectionTest 67 项通过；日志 `/private/tmp/android-thinking-consumer-tests.log`。
- Mermaid 独立验收：MermaidIosParserParityTest 5 项、MermaidGeometryParityTest 6 项通过；日志 `/private/tmp/android-mermaid-independent-review.log`。

## 本次公开 API 与兼容性

- 新增 opt-in Highlight/Superscript/Subscript 插件及节点、HTML `smallStyle`、Mermaid 路由/几何 tokens、GitGraph/Mindmap kinds 和可选量测参数；原有 Kotlin 函数/构造调用的参数顺序与默认参数保留。若消费者对 `MermaidKind` 使用无 `else` 的 exhaustive `when`，需补充 GitGraph/Mindmap 分支（或 `else`）后重新编译。
- `MermaidLayout.compute` 添加 `@JvmOverloads`，保留原来的单参数 JVM 入口；新量测入口是额外重载。
- `MarkdownStyleSheet`、`MarkdownMermaidTokens`、`MermaidPlacedEdge` 的 data class 末尾新增默认字段会改变完整 JVM constructor、`copy`/`copy$default` 签名（Dp 参数还会改变 mangled 方法名）。这是 Kotlin 源码兼容变更，**不保证旧预编译二进制直接替换 AAR 的 ABI 兼容**；消费者及依赖这些类型的预编译模块须重新编译。保留无参构造并不能消除此限制。
- 保持 generated data-class `copy` 全字段语义；不通过仅补旧 constructor 误称完全 ABI 兼容。
- API 基线仅精确排除本次真正 internal/private 的 HTML postprocessor、native tree parser 和无公开方法/仅 internal 方法的新文件 facade；旧公开类型与历史签名仍纳入核对。公开新插件、tokens、kind、layout metrics 均纳入基线。
- 此轮仅核实与修复，不构成版本发布；目前版本仍为 0.4.1。

## 本次集成收尾

- Android 0.5.0：新增 Mermaid native kinds / geometry tokens / opt-in inline plugins，保留 Kotlin 1.9.24、Compose 1.6.8、compileSdk 35。
- 完整库回归：530 项，527 通过，3 个显式性能基准跳过，无失败；公开 JVM API 基线经独立审阅并通过。
- 独立审阅补出的 class inheritance 箭头方向、Gantt Today 日期重叠、同行 disclosure 无效前缀及脚注问题均已修复。
- DimRemote 同步多列横向表格、统一单元格行高、块间距、HTML/格式插件、紧凑代码头、继承图片 loader/headers/cachePolicy 及 assistant thinking 投影；持久化原文不变。
- 验证使用独立 QA applicationId；公开版本接入与真机/模拟器截图结果由消费项目验收记录追踪。
