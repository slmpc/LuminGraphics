# LuminGraphics 0.1.0

LuminGraphics is a Java 17 graphics library stack over PrismRHI. Its published
modules are `lumin-graphics-core`, `lumin-graphics-render`,
`lumin-graphics-text`, `lumin-graphics-ui`, and `lumin-graphics-bom`; the demo
is an internal smoke application.

Start with the [documentation index](docs/README.md). The migration uses an
isolated local Maven repository for Prism and Lumin artifacts; no public
registry workflow is supported or documented.

```powershell
.\gradlew.bat -Dmaven.repo.local=D:\m2-prism check
.\gradlew.bat -Dmaven.repo.local=D:\m2-prism architectureCheck
```

The module direction is `core -> render -> text -> ui`. Shader inputs compile
to deterministic generated resources; migration and retained assets are
tracked by their linked ledgers rather than copied ad hoc.
