# LuminGraphics 1.0.0

LuminGraphics is a Java 17 graphics library stack over PrismRHI. Its published
modules are `lumin-graphics-core`, `lumin-graphics-render`,
`lumin-graphics-text`, `lumin-graphics-ui`, and `lumin-graphics-bom`; the demo
is an internal smoke application.

Start with the [documentation index](docs/README.md).

Chinese documentation is available from [docs/zh-CN](docs/zh-CN/README.md),
including setup, resource ownership, text/font integration, and UI rendering.

The module direction is `core -> render -> text -> ui`. Shader inputs compile
to deterministic generated resources; migration and retained assets are
tracked by their linked ledgers rather than copied ad hoc.
