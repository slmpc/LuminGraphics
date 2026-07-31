# LuminGraphics 1.0.0

LuminGraphics is a Java 17 graphics library stack over PrismRHI. Its published
modules are `lumin-graphics-core`, `lumin-graphics-render`,
`lumin-graphics-text`, `lumin-graphics-ui`, and `lumin-graphics-bom`; the demo
is an internal smoke application.

Start with the [documentation index](docs/README.md). Prism resolves through
Gradle `mavenLocal()` and is never fetched from a public registry. Maven's
default local repository is used unless `-Dmaven.repo.local` selects another.

```powershell
.\gradlew.bat -Dmaven.repo.local=D:\m2-prism check
.\gradlew.bat -Dmaven.repo.local=D:\m2-prism architectureCheck
```

The module direction is `core -> render -> text -> ui`. Shader inputs compile
to deterministic generated resources; migration and retained assets are
tracked by their linked ledgers rather than copied ad hoc.
