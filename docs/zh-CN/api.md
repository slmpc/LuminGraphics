# API 使用手册

## 模块选择

| 模块 | 依赖 | 主要入口 | 适用场景 |
| --- | --- | --- | --- |
| `lumin-graphics-core` | PrismRHI | `LuminGraphicsContext`、`ResourceRegistry` | 上下文、尺寸、资源和坐标转换 |
| `lumin-graphics-render` | core | `RenderFrame`、`Render2DScheduler`、`LuminPipelineCatalog` | 2D 命令、即时绘制、管线和 shader |
| `lumin-graphics-text` | render | `FontResource`、`TtfFontLoader`、`TextRenderer` | TTF 字形、图集、布局和文本批处理 |
| `lumin-graphics-ui` | text | `UiTree`、`LuminUiRenderer` | 声明式 UI 树和控件渲染 |

只依赖所需的最高层模块。UI 模块已经传递依赖其前面的三个模块；业务代码不应建立反向依赖。

## Core：线程与资源

`LuminGraphicsContext` 是 Lumin 层的生命周期边界，而不是 Prism 上下文的替代品。它保存调用方设备、渲染线程门禁、当前 surface 指标与当前 render target 的查询函数。

- 访问 Prism 设备、surface 或注册表前会校验渲染线程。
- `ResourceRegistry.register(...)` 对每个名字只接受一个资源，并保存 Prism 的 `RhiOwnership` 与失效令牌。
- 调用 `invalidateResources()` 后，先重新创建需要恢复的应用资源，再进入下一帧。
- 关闭注册表时资源以注册的逆序关闭，方便后创建的对象先释放依赖。

## Render：命令与 shader

`Render2DScheduler` 收集按 layer 排序的 2D 命令，renderer 在一个已开始的 `RenderFrame` 内消费命令。规划器先在每个手动 layer 内按 pipeline、scissor 和采样纹理聚合命令，再只为数量较少的批次组建立遮挡依赖；规划成本因此取决于批次组数，而不是图元数。同一 layer 优先合批，必须精确保序的前景与背景应放入不同 layer；不同 layer 中互不相交的 Panel、背景和文字仍可跨层重排并合批。纹理、字体 Atlas 和 scissor 都属于精确批次键，不会跨资源误合并。`LuminPipelineCatalog.entries()` 是稳定的管线描述表；不要根据文件名手写重复的 shader 路径。

构建 Vulkan shader 资源：

```powershell
.\gradlew.bat :lumin-graphics-render:compileShaders shaderCompileTest
```

GLSL 源码是保留资源；生成的 SPIR-V 由任务产生，不能手工修改。

## Text：图集、异步字形与关闭

`TtfFontLoader` 对同一 code point 合并并发请求，生成的 `GlyphDescriptor` 指向一个图集页。构造时指定像素高度、padding、图集页大小、最大页数、上传器与执行器。

- `requestGlyph(int)` 返回可取消的 future；需要同步结果时使用 `requireGlyph(int)`。
- 关闭 loader 会取消尚未完成的请求，并关闭所有已上传图集页。
- `FontRegistry` 以应用定义的字符串 ID 管理多个 loader；UI 的 `UiResourceResolver.font(id)` 负责把节点字体 ID 映射到它们。
- `TextRenderer.add(...)` 只积累绘制批次；在正确的 scheduler 层绑定后调用 `draw()`，再于帧结束调用 `clear()`。

## UI：构建、校验和渲染

`UiTree.build(scope -> { ... })` 使用 `UiTree.Scope` 构建一帧不可变的节点快照。坐标默认相对当前 scope；`push` 建立相对区域，`pushAbsolute` 使用绝对区域，`scissor` 建立裁剪树。scissor 只裁剪最终输出，子节点可超出其边界以支持滚动与展开动画。`scissorIf` 可在不改变调用方 scope 的前提下按条件建立裁剪；`Stack.item` 可同时向回调提供已解析的绝对 `UiRect` 和 item scope。字体与纹理通过稳定的字符串 ID 引用，viewport 目标使用公开的 `UiViewportTarget`。

```java
UiTree tree = UiTree.build(ui -> {
    ui.roundRect(12, 12, 240, 80, 8, new LuminColor(.12f, .16f, .22f, 1));
    ui.text("你好，LuminGraphics", 24, 34, .7f, new LuminColor(1, 1, 1, 1));
});

uiRenderer.render(tree, batch, 0);
```

`LuminUiRenderer.render(...)` 会先校验 UI 树，再把节点转换为 scheduler 命令。纹理和字体由 `UiResourceResolver` 提供，主题参数由 `UiRenderBatch` 提供。应用应在每帧重建或更新自己的 `UiTree`，不应跨设备或跨渲染线程复用资源解析器。

## 错误处理

核心模块抛出 `LuminException` 的具体子类，文本模块抛出 `FontException` 的具体子类，UI 校验失败抛出 `UiMalformedTreeException`。这些异常都表示调用顺序、资源所有权、输入数据或线程使用不满足公开契约；应在应用帧边界记录并恢复，而不是忽略后继续提交同一帧。
