# Core Module Notes

## Scope

Core provides library-facing contexts, render targets, resource/threading
contracts, geometry, vertex data, and texture primitives.

## Entrypoints

| Area | Location |
| --- | --- |
| Contexts | `src/main/java/.../core/context/LuminGraphicsContext.java`, `RenderContext.java` |
| Targets | `src/main/java/.../core/target/RenderTarget.java` |
| Resources and threading | `src/main/java/.../core/resource/`, `threading/` |
| Geometry and vertices | `src/main/java/.../core/geometry/`, `vertex/` |
| Textures | `src/main/java/.../core/texture/` |

## Tests

Targeted core contracts and fixtures are under `src/test/java` and
`src/testFixtures`. Run `..\\gradlew.bat :lumin-graphics-core:test` for core-only
changes.

## Pitfalls

- Treat a `RenderContext` and `RenderTarget` lifecycle as caller-controlled.
- Keep resource access within the module's threading/resource abstractions;
  callers must not bypass context checks with native backend state.
- Add backend-neutral contracts here only. Renderer, font, and widget behavior
  belongs in their downstream module.
- Preserve public value semantics for geometry, vertex, and texture inputs so
  render/text/UI can reuse them without hidden backend coupling.

## Assets

This module owns no shader or font manifest entries. Those live in render/text
resources and are tracked from `docs/resources/manifest.csv`.
