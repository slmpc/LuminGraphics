# LuminGraphics Knowledge Base

## Overview

LuminGraphics is a Java 17 library stack over PrismRHI: core, render, text,
and UI are published in one direction; the demo is a non-published smoke app.

## Structure

```text
lumin-graphics-core/       contexts, resources, threading, geometry
lumin-graphics-render/     renderers, pipelines, shader catalog
lumin-graphics-text/       fonts, glyph atlases, layout, text rendering
lumin-graphics-ui/         UI tree, controls, layout, UI renderer
lumin-graphics-demo/       caller-owned OpenGL/Vulkan smoke applications
lumin-graphics-bom/        published dependency alignment
docs/migration/            53-row Epsilon surface ledger
docs/resources/            shader/font resource manifest
```

## Where To Look

| Task | Location | Notes |
| --- | --- | --- |
| Public module graph | `build.gradle.kts` | One-way published API edges. |
| Core API | `lumin-graphics-core/src/main/java` | Context and resource contracts. |
| Shader pipeline | `lumin-graphics-render/build.gradle.kts` | Generates Vulkan SPIR-V resources. |
| Demo modes | `lumin-graphics-demo/build.gradle.kts` | GL, Vulkan, and negative smokes. |
| Migration scope | `docs/migration/epsilon-surface.csv` | 53 migrated API rows. |
| Resource scope | `docs/resources/manifest.csv` | 37 retained shader entries; fonts are caller-owned. |

## Code Map

| Symbol/area | Location | Role |
| --- | --- | --- |
| `LuminGraphicsContext` | core `.../core/context/` | Library lifecycle/context surface. |
| `LuminRenderPipelines` | render `.../render/pipeline/` | Pipeline catalog. |
| `TextRenderer` | text `.../text/render/` | Text batching/rendering. |
| `UiTree` | UI `.../ui/tree/` | UI hierarchy/layout root. |
| `StandaloneSmoke` | demo `.../demo/` | Caller-owned smoke entrypoint. |

Reference centrality is explicitly unmeasured: no LSP or codegraph service is
configured for this repository.

## Conventions

- Preserve the published direction `core -> render -> text -> ui`.
- Compile GLSL through the existing deterministic `compileShaders` task.
- Keep imported migration/resource ledgers source-relative and auditable.
- Use Java 17 and the root's strict `-Xlint:all -Werror` compilation settings.

## Anti-Patterns

- Do not add an API edge from an earlier published module to a later one.
- Do not publish `lumin-graphics-demo`.
- Do not edit retained resource bytes or ledger hashes without their source
  migration work and validation.

## Commands

```powershell
.\gradlew.bat check
.\gradlew.bat architectureCheck
.\gradlew.bat :lumin-graphics-render:compileShaders shaderCompileTest
.\gradlew.bat gl41Smoke gl46Smoke vulkanSmoke
```

## Documentation

The [root README](README.md) links to [docs](docs/README.md). Keep the API
guide and both manifest READMEs linked when adding documentation.
