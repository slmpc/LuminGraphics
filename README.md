# LuminGraphics 1.2.1

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

For offline verification against a local Maven mirror, pass
`-PdependencyRepository=D:/path/to/maven-repository`. Publications continue to
use the separate `publishRepository` property.

## Local SNAPSHOT publishing

Development publications use `1.1.0-SNAPSHOT`. Republish the same coordinate
after each edit to the shared local repository without changing the version:

```powershell
.\gradlew.bat publish -PpublishRepository=D:\Dev\ChenMeng\maven-repository
```
