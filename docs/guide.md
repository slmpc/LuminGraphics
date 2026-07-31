# LuminGraphics Library Guide

## Modules And API Surface

`lumin-graphics-core` owns `LuminGraphicsContext`, `RenderContext`,
`RenderTarget`, resource/threading, geometry, vertex, and texture contracts.
`lumin-graphics-render` adds immediate rendering, scheduler/renderer behavior,
`LuminPipelineCatalog`, `LuminRenderPipelines`, and shader support.
`lumin-graphics-text` adds `FontResource`, `FontRegistry`, `TtfFontLoader`,
`GlyphAtlas`, `TextLayout`, and `TextRenderer`. `lumin-graphics-ui` adds
`UiTree`, `UiNode`, theme/viewport, controls, state/scenes, and
`LuminUiRenderer`.

The published direction is one way: `core -> render -> text -> ui`. Keep
caller-owned graphics context/resource lifetime visible at every boundary.

## Shaders And Resources

Run `:lumin-graphics-render:compileShaders` to transform retained GLSL under
`lumin-graphics-render/src/main/resources/assets/lumin_graphics/shaders` into
generated Vulkan 1.3/SPIR-V 1.6 resources. Validate with `shaderCompileTest`;
`shaderGl41Test` and `shaderGlDsaTest` additionally compile/link the catalog in
hidden OpenGL contexts. Never edit generated outputs.

The resource manifest contains 37 shader entries. Font data is not packaged:
callers provide a `FontResource`, normally with `FontResource.path(Path)`, when
creating a `TtfFontLoader`. The migration ledger contains 53 rows, with the
four published graphics modules plus version-specific common surfaces. Read
[resources](resources/README.md) and [migration](migration/README.md) before
changing either ledger.

## Demo

`lumin-graphics-demo` supplies `StandaloneSmoke`, `VulkanStandaloneSmoke`, and
`CallerOwnedVulkanContext`. Run `gl41Smoke`, `glDsaSmoke`, `vulkanSmoke`,
`wrongContextSmoke`, or `missingShaderSmoke` through the root wrapper.
