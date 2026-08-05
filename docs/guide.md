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

## Ordered 2D Batching

`Render2DScheduler` treats each integer layer as a strict ordering boundary.
Commands are collected into per-layer buckets at submission time, and flush
plans every layer independently in ascending order. Within one layer,
`Render2DBatchPlanner` collapses commands with the same exact key (pipeline,
scissor, and sampled texture) into a single batch group. Pipeline groups are
ordered by their first appearance, and exact state groups remain in first
appearance order inside each pipeline, so interleaved pipelines become
contiguous pipeline runs. Glyphs from different atlas pages never share a draw,
and segmented shadows always render as isolated batches even when their
pipeline run is adjacent.

The planner never rebuilds a spatial index or a painter-order dependency graph:
its cost is O(N + G) per layer (N commands, G batch groups) and all scratch
buffers are reused across frames, which keeps typical 1k-2k command GUI frames
cheap. Exact foreground/background ordering is expressed manually with
`UiTree.Scope.layer(...)`, layered primitive overloads, or a scene-relative
layer; distinct layers are never merged or reordered.

## Shaders And Resources

Run `:lumin-graphics-render:compileShaders` to transform retained GLSL under
`lumin-graphics-render/src/main/resources/assets/lumin_graphics/shaders` into
generated Vulkan 1.3/SPIR-V 1.6 resources. Validate with `shaderCompileTest`;
`shaderGl41Test` and `shaderGl46Test` additionally compile/link the catalog in
hidden OpenGL contexts. Never edit generated outputs.

The resource manifest contains 37 shader entries. Font data is not packaged:
callers provide a `FontResource`, normally with `FontResource.path(Path)`, when
creating a `TtfFontLoader`. The migration ledger contains 53 rows, with the
four published graphics modules plus version-specific common surfaces. Read
[resources](resources/README.md) and [migration](migration/README.md) before
changing either ledger.

## Fullscreen Effects

`BlurShader`, `FxaaShader`, `FilterShader`, and `GlslSandbox` accept an explicit
`Render2DTexture` plus a caller-encoded `ByteBuffer` containing the fragment
shader's dynamic uniform block. The effect snapshots those bytes in a
`FullscreenEffectRequest`; `RenderResources.requireFullscreenEffectBinding`
must resolve a descriptor that combines that exact sampled input and uniform
payload. Its returned `FullscreenEffectPass` selects either the caller's
existing render pass (`external()`) or one balanced Prism dynamic-rendering
pass (`rendering(info)`). Sandbox application without an input and descriptor
payload is rejected.

## Default Render Resources

`DefaultRenderResources` supplies the standard catalog pipelines, per-frame
uniform descriptor, sampled texture descriptors, and segmented-shadow payloads
for callers that do not need a custom `RenderResources` implementation. Supply
frame uniforms through `updateFrameUniforms(ByteBuffer)`. The buffer must be
exactly `DefaultRenderResources.FRAME_UNIFORM_BYTES` (80) bytes: the std140
`mat4 Projection` followed by `vec4 Viewport`. The API deliberately accepts
raw bytes so it has no JOML or platform-specific dependency. The resource owner
must close the instance on the Prism render thread after dependent renderers
are closed.

Custom `RenderResources` implementations must return their shared frame-uniform
descriptor from `requireFrameDescriptor()`. Immediate untextured 2D draws bind
that descriptor automatically; sampled draws use `requireTextureDescriptor()`.

## Demo

`lumin-graphics-demo` supplies `StandaloneSmoke`, `VulkanStandaloneSmoke`, and
`CallerOwnedVulkanContext`. Run `gl41Smoke`, `gl46Smoke`, `vulkanSmoke`,
`wrongContextSmoke`, or `missingShaderSmoke` through the root wrapper.
