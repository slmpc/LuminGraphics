# Render Module Notes

## Scope

Render builds on core with immediate rendering, pipeline/catalog definitions,
scheduling, renderer implementations, and shader-facing infrastructure.

## Entrypoints

| Area | Location |
| --- | --- |
| Pipeline catalog | `src/main/java/.../render/pipeline/LuminPipelineCatalog.java` |
| Render pipelines | `src/main/java/.../render/pipeline/LuminRenderPipelines.java` |
| Renderer/scheduler | `src/main/java/.../render/renderer/`, `scheduler/` |
| Immediate mode | `src/main/java/.../render/immediate/` |
| Shader API | `src/main/java/.../render/shader/` |
| Compiler tool | `src/shaderCompiler/java/.../ShaderCompilerTool.java` |

## Assets And Tests

GLSL inputs are `src/main/resources/assets/lumin_graphics/shaders`; generated
SPIR-V resources are under `build/generated/resources/shaders`.

Run `..\\gradlew.bat :lumin-graphics-render:compileShaders shaderCompileTest`
for generated artifacts. `shaderGl41Test` and `shaderGlDsaTest` compile/link in
hidden OpenGL contexts when native graphics validation is required.

## Pitfalls

- Do not hand-edit generated shader resources or the generation-complete file.
- `compileShaders` targets Vulkan 1.3/SPIR-V 1.6 with `LUMIN_VULKAN=1`; preserve
  this contract when changing shader inputs.
- Keep shader compiler dependencies isolated in the existing source set.
- Use catalog entries rather than ad hoc pipeline construction when an existing
  rendering path already supplies the state contract.

## Integration

This module depends only on core. Text and UI consume its rendering surface;
do not import either downstream module here.
