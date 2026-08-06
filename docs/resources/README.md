# Epsilon resource manifest

See the [documentation index](../README.md) and [library guide](../guide.md)
for shader generation and module ownership.

`manifest.csv` records source bytes only; it does not copy or rewrite runtime assets. Its `source` paths are relative to `common/src/main/resources/assets/epsilon` in the Epsilon repository, while every `target` is in `assets/lumin_graphics/`.

The columns are:

- `type`: `shader`.
- `sha256` and `byte_size`: the exact lowercase SHA-256 and source byte count.
- `source_commit`: the Epsilon Git commit from which the bytes were inventoried.
- `provenance_status`: `source-repository; license-review-required` because no license is asserted by this extraction contract.
- `import_rewrite_status`: `rewrite-required-todo10` for shaders containing `#moj_import`; otherwise `not-applicable`.
- `target_test_id`: the byte or rewrite contract that owns subsequent verification.

The manifest remains source-relative and records the exact bytes and rewrite
status for each retained shader. Font files are caller-owned and are not tracked
or packaged.
