# Epsilon migration surface

`epsilon-surface.csv` is the source-to-owner contract for the Epsilon graphics and UI extraction. Its `source` paths are relative to `common/src/main/java/com/github/epsilon` in the source repository identified by `source_commit`.

The columns are:

- `destination_repository_module`: the repository and module that owns the replacement.
- `target_public_symbol`: the unique replacement API symbol; compatibility symbols under `com.github.epsilon` are forbidden.
- `disposition`: `port`, `protocol-replace`, or `move-to-MC`.
- `preserved_behavior`: the observable behavior that survives migration.
- `replacement_deletion_rationale`: why the original coupling is ported or replaced at that destination.
- `behavioral_test_id`: the contract that must preserve the behavior during implementation.
- `source_commit`: the exact Epsilon Git commit inventoried by the row.

`MigrationManifestTest` compares the ledger to the live source tree. The deterministic workspace default is `D:/Dev/OpenEpsilon/Epsilon-Private`; set `lumin.epsilon.root` to verify another checkout or `lumin.migration.manifest` to mutation-test a copy.
