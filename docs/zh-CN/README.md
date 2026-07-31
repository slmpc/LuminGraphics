# LuminGraphics 中文文档

LuminGraphics 是基于 PrismRHI 的 Java 17 图形库。公开模块按
`core -> render -> text -> ui` 单向依赖；应用程序拥有 Prism 上下文、交换链、窗口和字体文件。

## 阅读顺序

- [快速开始](getting-started.md)：本地 Maven、Gradle 依赖和最小渲染生命周期。
- [API 使用手册](api.md)：Core、Render、Text、UI 的职责、线程规则和资源关闭顺序。
- [英文库指南](../guide.md)：着色器编译、demo 和验证任务。
- [资源说明](../resources/README.md)：保留的 shader 资源与审计清单。
- [迁移台账](../migration/README.md)：从 Epsilon 迁移的公开 API 范围。

## 基本约束

1. 仅使用 Java 17。
2. PrismRHI 必须先发布到本地 Maven 仓库；LuminGraphics 不从公共仓库解析 Prism。
3. 所有 Prism 对象必须在创建它们的渲染线程上使用和关闭。
4. 字体不随 LuminGraphics 发布。应用自行提供 TTF/OTF，并通过 `FontResource.path(Path)` 传入。
5. `lumin-graphics-demo` 只用于 smoke 验证，不能作为应用运行时依赖。
