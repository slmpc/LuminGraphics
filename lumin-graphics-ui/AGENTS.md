# UI Module Notes

## Scope

UI provides widget/control state, the tree/layout/theme model, scenes, and the
renderer/batching bridge over the text module.

## Entrypoints

| Area | Location |
| --- | --- |
| Tree and nodes | `src/main/java/.../ui/UiTree.java`, `UiNode.java` |
| Viewport/theme | `src/main/java/.../ui/UiViewport.java`, `UiTheme.java` |
| Controls/widgets | `src/main/java/.../ui/control/`, `widget/` |
| State and scenes | `src/main/java/.../ui/state/`, `scene/` |
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
