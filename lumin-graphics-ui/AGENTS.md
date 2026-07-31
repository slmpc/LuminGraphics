# UI Module Notes

## Scope

UI provides widget/control state, the tree/layout/theme model, scenes, and the
renderer/batching bridge over the text module.

## Entrypoints

| Area | Location |
| --- | --- |
| Animation | `src/main/java/.../ui/animation/UiAnimation.java` |
| Controls | `src/main/java/.../ui/control/`, `UiScrollBar.java` |
| Geometry | `src/main/java/.../ui/geometry/UiRect.java`, `Insets.java` |
| Layout | `src/main/java/.../ui/layout/`, `LinearScope.java` |
| Container nodes | `src/main/java/.../ui/node/container/`, `Layer.java`, `Scissor.java` |
| Primitive nodes | `src/main/java/.../ui/node/primitive/`, `Rect.java`, `Text.java` |
| Tree and nodes | `src/main/java/.../ui/tree/UiTree.java`, `UiNode.java` |
| Viewport/theme | `src/main/java/.../ui/viewport/Viewport.java`, `.../ui/theme/UiTheme.java` |
| Resource resolution | `src/main/java/.../ui/resource/UiResourceResolver.java` |
| State and scenes | `src/main/java/.../ui/state/UiInvalidationState.java`, `.../ui/scene/UiScene.java` |
| Text metrics | `src/main/java/.../ui/text/UiTextMetrics.java` |
| Rendering | `src/main/java/.../ui/render/LuminUiRenderer.java`, `UiRenderBatch.java`, `UiContentBuffer.java` |

## Tests

Module tests are under `src/test/java`. Run `..\\gradlew.bat :lumin-graphics-ui:test`
for UI contract and renderer changes.

## Pitfalls

- Mutate tree/state through the UI model, then let layout/rendering consume it;
  do not retain raw render resources in widget state.
- Keep viewport and theme changes explicit so layout invalidation has a defined
  owner.
- Preserve UI batch ordering and clip/content buffer lifetime in renderer work.
- Keep Minecraft-specific UI adapters outside this published desktop module.

## Assets

UI uses text/render assets but owns no separate retained asset manifest. Add
shared assets through the documented render/text resource workflow.
