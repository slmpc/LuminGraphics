# LuminGraphics Documentation

All build and consumer workflows use Gradle `mavenLocal()` for PrismRHI. Maven's
default local repository is used unless `-Dmaven.repo.local=<absolute-path>`
selects another repository containing PrismRHI 0.1.0; no public registry is
part of this migration.

## Index

- [Repository README](../README.md): module graph and local setup.
- [Library guide](guide.md): API areas, render/shader, text/UI, and demo workflow.
- [Migration ledger](migration/README.md): 53 tracked source-surface rows.
- [Migration manifest](migration/epsilon-surface.csv): machine-readable ledger.
- [Resource guide](resources/README.md): retained source bytes and import rules.
- [Resource manifest](resources/manifest.csv): 37 shader and 4 font entries.

Use `check` for topology, `architectureCheck` for published API/JAR/ledger
contracts, and the render shader tasks for generated shader outputs.
