# 快速开始

## 1. 构建项目

```powershell
.\gradlew.bat check
```

LuminGraphics 的 group 为 `com.github.slmpc.lumingraphics`，当前版本由根 `build.gradle.kts` 定义。消费者通常通过 BOM 对齐版本：

```kotlin
dependencies {
    implementation(platform("com.github.slmpc.lumingraphics:lumin-graphics-bom:1.0.0"))
    implementation("com.github.slmpc.lumingraphics:lumin-graphics-ui")
}
```

## 2. 创建上下文

调用方负责创建 Prism 的 `RhiInstance`、`RhiDevice`、命令池和呈现目标。随后将这些对象包装成 `LuminGraphicsContext`。`RenderThreadGate` 必须代表创建 Prism 对象的线程，两个 supplier 必须返回当前帧的尺寸和目标。

```java
LuminGraphicsContext context = new LuminGraphicsContext(
        device,
        renderThreadGate,
        () -> new SurfaceMetrics(framebufferWidth, framebufferHeight, contentScale),
        () -> new RenderTarget(colorView, framebufferWidth, framebufferHeight, device.contextIdentity())
);
```

`context.device()`、`context.metrics()`、`context.renderTarget()` 和 `context.resources()` 都会检查渲染线程和关闭状态。窗口 resize 后，supplier 必须立即提供匹配的新尺寸和目标。

## 3. 每帧提交

渲染模块的 `RenderFrame` 只负责命令缓冲的 `begin/end` 配对；它不会提交，也不会等待队列。调用方在 frame 关闭后自行提交并同步：

```java
try (RenderFrame frame = new RenderFrame(commands, renderResources, frameId, completedFrameId, width, height)) {
    RenderExecution execution = frame.execution();
    // 记录 Render2DScheduler、LuminImmediateRenderer 或 LuminUiRenderer 的绘制命令。
}
device.queue(RhiQueueType.GRAPHICS).submit(RhiSubmitInfo.of(commands));
```

## 4. 配置字体

LuminGraphics 不包含任何字体。应用选择授权允许分发的字体，并用绝对或相对文件路径创建 `FontResource`：

```java
FontResource fontFile = FontResource.path(Path.of("assets/fonts/NotoSansCJK-Regular.ttf"));
try (TtfFontLoader font = new TtfFontLoader(
        fontFile, 32, 2, 1024, 1024, 4, glyphAtlasUploader, glyphExecutor)) {
    // 将 font 注册到 FontRegistry 或直接交给 TextRenderer。
}
```

如果应用自己把字体放进 classpath，可以使用 `FontResource.classpath("assets/fonts/app.ttf")`。这仅查找调用方的 classpath，LuminGraphics 的 artifact 不提供默认字体。

## 5. 关闭顺序

推荐顺序是：停止新帧 -> 关闭 UI/Text/Render 辅助对象 -> 关闭 `LuminGraphicsContext` -> 关闭调用方的 Prism 设备和实例。所有关闭动作均在渲染线程执行。`LuminGraphicsContext.close()` 会逆序关闭已注册的资源，但不会替调用方关闭 `RhiDevice`。
