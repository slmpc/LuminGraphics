---
slug: lumin-graphics-prism-rhi-migration
status: approved
intent: clear
review_required: false
pending-action: write .omo/plans/lumin-graphics-prism-rhi-migration.md
approach: Refactor PrismRHI 0.1.0 first, publish it to mavenLocal, build standalone LuminGraphics in the new namespace, then build a separate LuminGraphics-MC dependency-mod repository whose exact-version AW/AT/Mixin accessors enable additional zero-copy Blaze3D bridges across Fabric/NeoForge x 26.1.2/26.2.
---

# Draft: lumin-graphics-prism-rhi-migration

## Components (topology ledger)
<!-- Lock the SHAPE before depth. One row per top-level component that can succeed or fail independently. -->
<!-- id | outcome (one line) | status: active|deferred | evidence path -->

| id | outcome | status | evidence path |
| --- | --- | --- | --- |
| prism-api | PrismRHI uses explicitly supplied providers, externally initialized GL/Vulkan state, manual shader binaries/native wrappers, and typed native-object lookup; no ServiceLoader or window creation remains | active | `D:\Dev\ChenMeng\PrismRHI\.omo\evidence\` |
| lumin-core | Standalone LuminGraphics implements the extracted renderer, scheduler, text, shader, and resource lifecycle capabilities through PrismRHI with no Minecraft/Blaze3D/loader/window dependency | active | `D:\Dev\ChenMeng\LuminGraphics\.omo\evidence\` |
| lumin-ui | Standalone LuminGraphics contains the declarative UI tree, scene/layer/batch, layout/state/control, and RHI renderer layers with Epsilon business dependencies replaced by public protocols | active | `D:\Dev\ChenMeng\LuminGraphics\.omo\evidence\` |
| mc-bridge | Separate LuminGraphics-MC dependency mod supplies version-gated Blaze3D-to-Lumin and Lumin-to-Blaze3D borrow/copy/rebuild adapters with explicit ownership and render-thread contracts | active | `D:\Dev\ChenMeng\LuminGraphics-MC\.omo\evidence\bridge\` |
| mc-matrix | LuminGraphics-MC builds Fabric and NeoForge artifacts for both 26.1.2 and 26.2 from one repository without leaking loader APIs into shared code | active | `D:\Dev\ChenMeng\LuminGraphics-MC\.omo\evidence\matrix\` |
| delivery | All three repositories have atomic Git history, scoped AGENTS.md knowledge, docs, Maven-local development flow, tests, and final review evidence | active | per-repository `.omo\evidence\delivery\` |

## Open assumptions (announced defaults)
<!-- Record any default you adopt instead of asking, so the user can veto it at the gate. -->
<!-- assumption | adopted default | rationale | reversible? -->

| assumption | adopted default | rationale | reversible? |
| --- | --- | --- | --- |
| Repository boundary | Three sibling Git repositories: existing PrismRHI, standalone LuminGraphics, and standalone LuminGraphics-MC | Explicit user correction; keeps Minecraft versions out of the reusable core | no after publication |
| Dependency direction | `LuminGraphics-MC -> LuminGraphics -> PrismRHI`; Epsilon-Private remains read-only source/reference and is not migrated to consume the new artifacts in this request | Prevents cycles and keeps extraction independent from Epsilon business code | yes |
| Java levels | PrismRHI and standalone LuminGraphics stay Java 17-compatible; LuminGraphics-MC uses Java 25 required by Minecraft 26.x | Preserves non-Minecraft reuse while matching the verified game toolchain | yes |
| Multi-version layout | One LuminGraphics-MC repository with a shared bridge API plus explicit 26.1.2 and 26.2 version builds, each producing Fabric and NeoForge artifacts | User asked for a multi-version project; a single source set cannot safely assume Mojang API compatibility | yes |
| Version pins | 26.1.2 follows Epsilon's verified catalog; initial 26.2 pins use Fabric Loader 0.19.3, Fabric API 0.156.0+26.2, NeoForm 26.2-2, NeoForge 26.2.0.37-beta | These exist in official metadata on 2026-07-30; actual Gradle resolution remains a RED/GREEN gate | yes |
| Shader compilation | Prism core accepts caller-supplied descriptor + backend binary; optional shaderc remains an explicitly constructed helper passed by the caller, never auto-discovered | Matches NVRHI `createShader(desc,binary,size)` and the no-Service requirement | yes |
| Window/context ownership | RHI never initializes GLFW/SDL, creates a window/surface/context, or destroys caller-owned native objects | Explicit user requirement | no |
| Blaze3D private access | Use exact-version Access Widener, Access Transformer, or Mixin accessors where public Blaze3D APIs do not expose compatible GL handles; retain capability/fallback results for unsupported objects | User selected option B; isolates fragile access to LuminGraphics-MC version modules | yes, but API behavior is public |
| Public namespace | `com.github.slmpc.lumingraphics` and `com.github.slmpc.lumingraphics.mc`; no Epsilon compatibility facade | User selected option A | no after publication |
| Prism compatibility | Remove the legacy ServiceLoader/GLFW/ownership APIs and publish the breaking contract as 0.1.0 | User selected option A; current release is only 0.0.1 | no after publication |
| Test strategy | TDD for all behavior changes, characterization tests before Prism refactors, real Gradle builds for all published/matrix surfaces | Required by ultrawork and appropriate for the blast radius | yes |

## Findings (cited - path:lines)

- `D:\Dev\ChenMeng\LuminGraphics` is empty except this `.omo` draft and is not a Git repository. `D:\Dev\ChenMeng\LuminGraphics-MC` exists, is empty, and is not a Git repository.
- Epsilon's extraction surface contains 53 Java files: 41 under `common/src/main/java/com/github/epsilon/graphics` and 12 under `common/src/main/java/com/github/epsilon/gui/lib`, plus 37 shader files and 4 TTF resources; no tests cover it.
- `D:\Dev\OpenEpsilon\Epsilon-Private\common\src\main\java\com\github\epsilon\graphics\LuminRenderSystem.java:3-22` imports Epsilon holders/settings, Mojang GPU objects, Minecraft window/input state, and JOML. `Render2DScheduler.java:27-295` centralizes render layers, passes, text, scissor, and flushing. `gui/lib/UiTree.java:23-1185` is the UI IR; `gui/lib/render/LuminUiRenderer.java:18-479` compiles that IR into scheduler commands.
- `D:\Dev\ChenMeng\PrismRHI\prism-rhi-core\src\main\java\com\github\slmpc\prismrhi\PrismRHI.java:17-38` discovers backends with ServiceLoader. `shader/RhiShaderCompilers.java:14-38` does the same for compilers. Backend service files exist in each backend/shaderc resource tree.
- `D:\Dev\ChenMeng\PrismRHI\prism-rhi-core\src\main\java\com\github\slmpc\prismrhi\shader\RhiShaderModuleCreateInfo.java:6-46` already carries stage/type/binary/entry point, and `pipeline/RhiGraphicsPipelineCreateInfo.java:67-74` already receives shader module handles. The plan therefore evolves these into the NVRHI contract and removes implicit compilation rather than duplicating an existing concept.
- `D:\Dev\ChenMeng\PrismRHI\prism-rhi-core\src\main\java\com\github\slmpc\prismrhi\resource\RhiResource.java:5-11` exposes one untyped `long nativeHandle()`. NVRHI commit `8e8c36e37558acec333204619b95d9d2fcdc4a79` instead defines extensible `ObjectType` values and `getNativeObject(ObjectType)` in `include/nvrhi/common/resource.h:31-118`.
- Prism Vulkan context modes include AUTO_GLFW_WINDOW/GLFW_WINDOW at `context/RhiContextMode.java:3-8`; `backend/vulkan/VulkanContext.java:69-127,181-195` initializes GLFW, creates window/surface, and conditionally destroys them. These paths directly violate the corrected target contract and must be deleted, not hidden.
- NVRHI commit `8e8c36e...` accepts caller-provided shader binary at `include/nvrhi/nvrhi.h:3701-3703` and existing Vulkan instance/device/queues at `include/nvrhi/vulkan.h:50-84`.
- Official metadata on 2026-07-30 reports both 26.1.2 and 26.2 stable in Fabric metadata; available 26.2 artifacts include Fabric Loader 0.19.3, Fabric API 0.156.0+26.2, NeoForm 26.2-2, and NeoForge 26.2.0.37-beta.
- Minecraft 26.1.2 local source is OpenGL-only under `reference/vanilla-26.1.2/com/mojang/blaze3d/opengl`; no Vulkan backend is present. `GlTexture.java:77-79` publicly exposes a GL texture id, while `GlBuffer.java:13-28` keeps its handle protected. `CommandEncoder.java:19-68,111-162` and `RenderPass.java:19-125` are lifetime-bound facades with no transferable native handle. `RenderSystem.java:41-101` enforces the render thread.
- Safe bridge semantics therefore vary by object: textures may support borrowed zero-copy in the same GL context; buffers generally require copy/map unless a version-specific accessor is explicitly accepted; logical pipelines/shaders are rebuilt; command encoders/render passes are adapted in-place and never converted into independently owned RHI objects.
- `D:\Dev\ChenMeng\LuminGraphics-MC` is now an explicit writable workspace root; it remains empty and uninitialized until execution.

## Decisions (with rationale)

- Refactor PrismRHI before LuminGraphics so downstream code targets the final explicit-provider/native-object contract and consumes verified mavenLocal artifacts.
- Keep all Minecraft and Blaze3D symbols in LuminGraphics-MC. LuminGraphics may expose generic resource/command/window protocols but no Minecraft adapter class.
- Recreate the behavioral surface of all 53 target files and resources, but do not preserve Epsilon holders, ClientSetting, Minecraft font adapters, or class-for-class implementation when public protocols are cleaner. Minecraft-specific adapters move to LuminGraphics-MC.
- LuminGraphics-MC bridge results carry conversion mode, ownership, source identity/version, context identity, invalidation/lifetime token, and an explicit unsupported reason. Borrowed wrappers never close Blaze3D-owned resources.
- Do not promise Vulkan Blaze3D interop for 26.1.2/26.2 without source/runtime evidence. The SPI remains backend-extensible and returns unsupported rather than inventing handles.
- Publish PrismRHI and LuminGraphics to mavenLocal first; LuminGraphics-MC resolves both locally during development. Public registry choice remains out of scope until a later release decision, per user instruction.
- Generate AGENTS.md by init-deep after real module boundaries exist; document architecture, external-context ownership, native-object/bridge contracts, build matrix, mavenLocal workflow, and migration mapping.
- Apply the user's selected B bridge strategy only inside exact-version LuminGraphics-MC modules: Fabric uses Access Widener/Mixin accessors and NeoForge uses Access Transformer/Mixin accessors as required; shared bridge APIs never expose loader-specific mechanisms.
- Use the selected clean namespace and PrismRHI 0.1.0 break; do not add compatibility facades or preserve old discovery/window factories.

## Scope IN

- Breaking or additive PrismRHI API work required to eliminate ServiceLoader, implicit compiler discovery, GLFW/window creation, and RHI-owned native contexts.
- NVRHI-style typed native-object lookup, caller binary shader creation, and borrowed/owned native resource wrappers needed by bridge code.
- Standalone LuminGraphics graphics resources, renderers, immediate/scheduled 2D/3D paths, shader effects, TTF/text/emoji support, declarative UI, scene/layer/batch/control/state APIs, resources, tests, docs, and Git repository.
- Standalone LuminGraphics-MC dependency mod, Blaze3D bridge SPI/adapters, Fabric/NeoForge entry points, 26.1.2/26.2 isolation, tests, docs, AGENTS.md, and Git repository.
- mavenLocal publication/consumption and deterministic build/test tasks across all three repositories.

## Scope OUT (Must NOT have)

- No RHI-created GLFW/SDL window, Vulkan/OpenGL context, Vulkan surface, or ownership of caller-native objects.
- No ServiceLoader or `META-INF/services` for backend/compiler creation or lookup.
- No Minecraft, Blaze3D, Fabric, or NeoForge dependency in standalone LuminGraphics.
- No Epsilon HUD/modules/settings/holders/managers/mixins outside the minimum source facts needed to extract the graphics/UI behavior; Epsilon-Private is not modified to consume the new libraries.
- No reflection or private-field access disguised as a stable bridge; a version-specific access transformer/mixin is used only if explicitly selected below and tested on both loaders.
- No fake zero-copy contract for objects whose public API cannot expose compatible handles; no claimed Vulkan bridge without verified backend support.
- No public registry/release migration in this task; local Maven is the integration surface.

## Resolved questions

1. **Blaze3D bridge contract: B.** Exact-version AW/AT/Mixin access to internal GL buffer/program ids is allowed for additional zero-copy paths. The public API still reports borrow/copy/rebuild/unsupported so version drift fails explicitly.
2. **Public namespace/compatibility: A.** Use `com.github.slmpc.lumingraphics` and `com.github.slmpc.lumingraphics.mc`; add no `com.github.epsilon` compatibility facade.
3. **PrismRHI compatibility: A.** Remove old ServiceLoader/GLFW/ownership APIs and release the new contract as 0.1.0.

Test strategy default: TDD with characterization tests for existing Prism behavior, failing-first contract tests for each new API, fake-backend unit tests for LuminGraphics, version-specific bridge tests, and real Gradle builds for all four MC variants.

## Approval gate
status: approved
<!-- When exploration is exhausted and unknowns are answered, set status: awaiting-approval. -->
<!-- That durable record is the loop guard: on a later turn read it and resume at the gate instead of re-running exploration. -->

Approved 2026-07-30 with decisions `B/A/A`. Write `.omo/plans/lumin-graphics-prism-rhi-migration.md` in dependency order `PrismRHI -> mavenLocal -> LuminGraphics -> mavenLocal -> LuminGraphics-MC`. Approval authorizes plan creation only; implementation begins later via start-work.
