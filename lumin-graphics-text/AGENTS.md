# Text Module Notes

## Scope

Text provides font resources/registries, TTF loading, glyph atlases, layout,
batches, and rendering on top of the render module.

## Entrypoints

| Area | Location |
| --- | --- |
| Fonts | `src/main/java/.../text/font/FontResource.java`, `FontRegistry.java` |
| TTF data | `src/main/java/.../text/ttf/TtfFontFile.java`, `TtfGlyph.java` |
| Loading and atlas | `src/main/java/.../text/atlas/TtfFontLoader.java`, `TtfGlyphAtlas.java` |
| Layout | `src/main/java/.../text/layout/TextLayout.java`, `TextLayoutEngine.java` |
| Rendering | `src/main/java/.../text/render/TextRenderer.java`, `TtfTextRenderer.java` |
| Emoji | `src/main/java/.../text/emoji/SystemEmojiAtlas.java`, `EmojiGlyph.java` |
| Icons | `src/main/java/.../text/icon/IconChars.java` |

## Assets And Tests

Font inputs are retained under `src/main/resources/assets/lumin_graphics` and
listed in `../../docs/resources/manifest.csv`. Targeted tests are in
`src/test/java`; run `..\\gradlew.bat :lumin-graphics-text:test` for module work.

## Pitfalls

- Load fonts through the module's resource/registry paths; preserve the manifest
  source and license metadata.
- Do not allow glyph-atlas lifetime to outlive its render context.
- Keep layout independent of widgets: UI integration belongs downstream.
- Maintain batching boundaries so text rendering reuses the render pipeline
  rather than creating a separate native drawing route.

## Integration

The module consumes render only. Its public text contracts are the UI layer's
input; do not create a dependency back to UI controls or scenes.
