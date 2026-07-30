# lumin-graphics-prism-rhi-migration - Work Plan

## TL;DR (For humans)
<!-- Fill this LAST, after the detailed plan below is written, so it summarizes the REAL plan. -->
<!-- Plain English for a non-engineer: NO file paths, NO todo numbers, NO wave/agent/tool names. -->

**What you'll get:** A clean RHI 0.1.0 that only wraps caller-owned graphics contexts, a reusable standalone graphics/UI library extracted from Epsilon, and a separate Minecraft dependency mod for Fabric and NeoForge on both requested game versions. The Minecraft layer includes explicit two-way Blaze3D resource bridges and packages the required runtime libraries.

**Why this approach:** The repositories are built in dependency order so every downstream build consumes the exact published upstream binary. Version-specific accessors stay inside the Minecraft project, while ownership and unsupported cases remain explicit so native resources cannot be silently deleted or misused.

**What it will NOT do:** It will not let the RHI create windows or graphics contexts, retain automatic backend/compiler discovery, leak Minecraft APIs into the standalone library, or pretend every Blaze3D object can be converted safely. It will not publish to a public registry or modify Epsilon business features.

**Effort:** XL
**Risk:** High - native ownership, three graphics backends, private version-specific game internals, and four real loader/version runtimes must all agree.
**Decisions to sanity-check:** Private Blaze3D access is intentionally allowed only through exact-version access rules; new public namespaces replace Epsilon packages; PrismRHI compatibility is intentionally broken at 0.1.0.

Your next move: start execution in a worker session, or request the optional dual high-accuracy plan review first. Full execution detail follows below.

---

> TL;DR (machine): XL/high-risk three-repository migration delivering PrismRHI 0.1.0, standalone LuminGraphics 0.1.0, and four LuminGraphics-MC loader/version artifacts with real bridge/runtime verification.

## Scope
### Must have
- Three repositories with one-way dependencies: `LuminGraphics-MC -> LuminGraphics -> PrismRHI`; only PrismRHI pre-exists as a Git repository.
- PrismRHI 0.1.0 breaking API: caller passes an instantiated `RhiBackendProvider`; backend/compiler ServiceLoader APIs and every `META-INF/services` registration are removed.
- NVRHI-style `RhiNativeObjectType` + `RhiNativeObject` lookup, explicit `RhiOwnership`, context identity/invalidation, borrowed native resource wrappers, and manual shader creation from descriptor + caller bytes or a caller native shader object.
- Vulkan accepts an already initialized LWJGL `VkInstance`, `VkPhysicalDevice`, `VkDevice`, queues/family indices, enabled extensions/features, and optional caller-owned `VkSurfaceKHR`; OpenGL accepts the already-current `GLCapabilities`, owner thread, and caller context identity. Prism never initializes or owns those objects.
- LuminGraphics 0.1.0 Java 17 modules and GAVs: `com.github.slmpc.lumingraphics:lumin-graphics-core`, `lumin-graphics-render`, `lumin-graphics-text`, `lumin-graphics-ui`, plus `lumin-graphics-bom`; a non-published `lumin-graphics-demo` creates its own GLFW context and passes it downward.
- A machine-readable 53-row migration ledger covering the 41 `graphics` and 12 `gui.lib` Java files; every row names its destination, preserved behavior, replacement/deletion reason, and behavioral test. The three Minecraft font adapters move to LuminGraphics-MC.
- All 37 shader files and 4 TTF files are inventoried with source/target/SHA-256/provenance. Runtime assets use `assets/lumin_graphics/`; the nine `#moj_import` vertex shaders are rewritten to a backend-neutral uniform/input ABI and compiled/linked on OpenGL 4.1, OpenGL DSA, and Vulkan.
- LuminGraphics-MC 0.1.0 is a Java 25 dependency mod with stable mod id `lumin_graphics_mc`, group `com.github.slmpc.lumingraphics.mc`, a no-Minecraft `bridge-contract`, and six version/loader modules: `mc-26.1.2-common|fabric|neoforge` and `mc-26.2-common|fabric|neoforge`.
- Exact MC pins: Gradle 9.2.1, Loom 1.15.5, ModDevGradle 2.0.140; 26.1.2 uses Fabric Loader 0.19.2, Fabric API 0.150.0+26.1.2, NeoForm 26.1.2-1, NeoForge 26.1.2.76; 26.2 uses Fabric Loader 0.19.3, Fabric API 0.156.0+26.2, NeoForm 26.2-2, NeoForge 26.2.0.37-beta.
- Approved bridge option B: exact-version Fabric Access Widener / NeoForge Access Transformer and shared Mixin accessors may expose GL buffer/program/constructor state. Public bridge results remain `BORROWED_ZERO_COPY`, `COPIED`, `REBUILT`, or `UNSUPPORTED`, with explicit thread/context/ownership/invalidation data.
- Exact bridge minimums for both versions: textures, buffers, and shader modules support borrowed zero-copy in both directions on the same OpenGL context; samplers and logical pipelines rebuild; command encoders/render passes are in-place adapters only; Vulkan/unknown Blaze backends return `UNSUPPORTED` until real source/runtime support exists.
- An isolated Maven repository proves the chain: PrismRHI 0.1.0 publish -> LuminGraphics 0.1.0 resolve/publish -> all LuminGraphics-MC variants resolve/package. POM/JAR SHA-256 and `dependencyInsight` output must prove no stale/composite/project substitution.
- Root and justified module-level AGENTS.md plus docs for ownership, shaders, bridge matrix, version matrix, Maven-local workflow, resource provenance, API use, and migration mapping.

### Must NOT have (guardrails, anti-slop, scope boundaries)
- No `ServiceLoader`, backend/compiler discovery registry, or `META-INF/services` in PrismRHI.
- No PrismRHI call to GLFW/SDL window/context/surface creation or destruction; test/demo code may create and destroy its own window/context outside RHI before passing it in.
- No public legacy 0.0.1 discovery/context/window API or `nativeHandle()` compatibility facade; this is the approved clean 0.1.0 break.
- No Minecraft, Blaze3D, Fabric, NeoForge, `com.github.epsilon`, GLFW, or SDL dependency/import in the four published LuminGraphics modules.
- No loader-specific type in `bridge-contract` or either version-common public contract; no 26.1.2 accessor source reused blindly for 26.2.
- No reflection for private Blaze3D access. Only declared, exact-version AW/AT/Mixin accessors with compile and runtime drift tests are permitted.
- No universal zero-copy claim: unsupported/unsafe object directions fail with a typed reason; borrowed wrappers never delete source-owned GL/Vulkan objects.
- No Epsilon business HUD/module/holder/setting/manager migration and no changes to Epsilon-Private beyond read-only source comparison.
- No public registry deployment or release-policy decision; this task ends at reproducible isolated Maven/local artifacts.
- No tests added after behavior without a captured RED, no skipped/disabled tests, and no grep-only evidence for runtime shader/accessor/bridge behavior.

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- Test decision: TDD with JUnit Jupiter 5, Gradle TestKit/architecture tests, fake-RHI trace tests, real hidden-window backend tests, and self-terminating Minecraft client smoke mods. Existing Prism behavior is characterized before breaking changes; every new contract captures the named test RED for the intended missing behavior, then reruns the identical command GREEN.
- Evidence: `<attemptDir>/task-<N>-lumin-graphics-prism-rhi-migration.log` where `attemptDir` is the current Boulder attempt directory; outside ulw-loop use each repository's `.omo/evidence/`. Key aggregate logs are also copied to the criterion paths named in the active goal, with the corrected MC matrix path under `D:\Dev\ChenMeng\LuminGraphics-MC\.omo\evidence\`.
- Java diagnostics: compile all changed source sets with `-Xlint:all -Werror` except documented third-party/generated warnings fixed at source; run available LSP diagnostics when an LSP is exposed. No LSP/codegraph is currently exposed, so Gradle compilation, ArchUnit/source-set boundary tests, and exact reference scans are blocking substitutes.
- Runtime contexts are fixture-owned: GLFW/Vulkan/GL setup and teardown live only in tests/demos/smoke launchers. Every spawned client/process/window/temp Maven repository has a paired cleanup todo/receipt and no residual PID/port/temp path.
- Resource verification parses `docs/migration/epsilon-surface.csv` and `docs/resources/manifest.csv`, verifies exactly 53 Java rows / 37 shader rows / 4 font rows, checks SHA-256, compiles all shader variants, and opens every font with STBTruetype.
- Architecture verification fails on forbidden imports/dependencies, ServiceLoader/service descriptors, Prism GLFW creation calls, reflection in MC bridge code, cross-version source leakage, and loader types outside loader modules.
- Real surfaces: Prism external-context tests exercise caller-created hidden GLFW/Vulkan contexts; Lumin demo renders a deterministic pixel pattern and verifies a readback SHA; four MC client tasks load the real mod/accessors, bridge texture/buffer/shader in both directions, verify borrowed close does not delete the GL object, write JSON, and exit.

## Execution strategy
### Parallel execution waves
> Target 5-8 todos per wave. Fewer than 3 (except the final) means you under-split.

- **Wave 1 - PrismRHI 0.1.0 (Todos 1-6):** pin the old behavior, introduce native/provider/shader contracts, migrate Vulkan and both OpenGL backends, then migrate all consumers/docs and publish. Todo 2 is the common contract barrier; Todos 3-5 may run in parallel after it; Todo 6 joins them.
- **Wave 2 - Standalone LuminGraphics (Todos 7-14):** initialize the repository/modules, lock the migration/resource ledgers, then implement core/render/shaders/schedulers/text/UI and the real external-context demo. Todo 7 blocks all; Todo 8 blocks subsystem completion checks; Todos 9-13 use the stable Prism artifact and can split by module after shared protocols land; Todo 14 joins them.
- **Wave 3 - LuminGraphics-MC (Todos 15-22):** initialize the exact matrix, lock the bridge contract, generate and inspect both MC source sets, implement per-version common accessors, wire Fabric/NeoForge, package dependency mods, then run real client smokes. Do not implement 26.2 accessors before Todo 15's source gate.
- **Wave 4 - Knowledge/integration (Todos 23-25):** generate hierarchical AGENTS/docs after module boundaries exist, enforce architecture/scope guards, and reproduce the complete isolated Maven/build/runtime chain with clean Git histories.
- Maximum concurrency remains five agents including root. Within a wave, only tasks marked independent in the matrix may run concurrently; each child owns distinct files and must not revert other changes.

### Dependency matrix
| Todo | Depends on | Blocks | Can parallelize with |
| --- | --- | --- | --- |
| 1 | none | 2-6 | none |
| 2 | 1 | 3-6 | none |
| 3 | 2 | 6, 9-14 | 4, 5 |
| 4 | 2 | 6, 14, 15-22 | 3, 5 |
| 5 | 2 | 6, 14, 15-22 | 3, 4 |
| 6 | 3, 4, 5 | 7-25 | none |
| 7 | 6 | 8-14 | none |
| 8 | 7 | 14, 23-25 | 9 after shared package names are fixed |
| 9 | 7 | 10-14 | 8 |
| 10 | 9 | 11-14 | 12 |
| 11 | 9, 10 | 13, 14 | 12 |
| 12 | 9, 10 | 13, 14 | 11 |
| 13 | 8, 11, 12 | 14 | none |
| 14 | 8-13 | 15-25 | none |
| 15 | 14 | 16-22 | none |
| 16 | 15 | 17-22 | none |
| 17 | 16 | 19-22 | 18 |
| 18 | 15, 16 | 19-22 | 17 |
| 19 | 17, 18 | 21, 22 | 20 |
| 20 | 17, 18 | 21, 22 | 19 |
| 21 | 19, 20 | 22, 23-25 | none |
| 22 | 21 | 23-25 | none |
| 23 | 6, 14, 22 | 25 | 24 |
| 24 | 6, 14, 22 | 25 | 23 |
| 25 | 23, 24 | F1-F4 | none |

## Todos
> Implementation + Test = ONE todo. Never separate.
<!-- APPEND TASK BATCHES BELOW THIS LINE WITH edit/apply_patch - never rewrite the headers above. -->
- [x] 1. Pin PrismRHI behavior and capture the four contract REDs
  What to do / Must NOT do: In `D:\Dev\ChenMeng\PrismRHI`, first run the unchanged `RhiCreateInfoTest` and `ShadercCompilerTest` as characterization GREEN. Add reflection/fake-backend tests `RhiBackendInjectionTest`, `RhiNativeObjectTest`, `RhiShaderHandleTest`, and `ExternalContextOwnershipTest` that compile against 0.0.1 but fail on the absence of the approved provider/native/shader/external-only contract. Capture each RED separately; do not edit production code or commit a red tree.
  Parallelization: Wave 1 | Blocked by: none | Blocks: 2-6
  References (executor has NO interview context - be exhaustive): `prism-rhi-core/src/main/java/com/github/slmpc/prismrhi/PrismRHI.java:17-38`; `resource/RhiResource.java:5-11`; `shader/RhiShaderModuleCreateInfo.java:6-46`; `context/RhiContextMode.java:3-8`; `prism-rhi-backend-vulkan/.../VulkanContext.java:69-127,181-195`; existing `prism-rhi-core/src/test/.../RhiCreateInfoTest.java`; `prism-rhi-shaderc/src/test/.../ShadercCompilerTest.java`.
  Acceptance criteria (agent-executable): `\.\gradlew.bat :prism-rhi-core:test --tests '*RhiCreateInfoTest' :prism-rhi-shaderc:test --tests '*ShadercCompilerTest'` exits 0 before edits; each new named test command exits nonzero with its intended assertion message, not a syntax/import/setup error; `git diff -- production-paths` is empty.
  QA scenarios (exact tool + invocation): happy baseline: PowerShell `\.\gradlew.bat :prism-rhi-core:test --tests '*RhiCreateInfoTest' :prism-rhi-shaderc:test --tests '*ShadercCompilerTest' --stacktrace`; failure proof: run each new `--tests '*<Name>'` selector and assert `FAILED` plus the contract-specific message. Evidence `<attemptDir>/task-1-lumin-graphics-prism-rhi-migration.log`.
  Commit: N | RED evidence remains uncommitted until the owning GREEN task

- [x] 2. Add the explicit-provider and typed native-object core contract
  What to do / Must NOT do: Add `RhiNativeObjectType` (extensible numeric id + diagnostic name), `RhiNativeObject` (integer/pointer-sized long), `RhiOwnership`, `RhiContextIdentity`, and thread-safe `RhiInvalidationToken`. Add `RhiResource.getNativeObject(type)` and typed adoption descriptors. Add `PrismRHI.createInstance(RhiBackendProvider, RhiInstanceCreateInfo)` and make provider identity authoritative rather than `createInfo.backend()`. Keep legacy members only as an internal staging bridge until Todo 6 so every intermediate commit builds; do not expose a second registry or cache.
  Parallelization: Wave 1 | Blocked by: 1 | Blocks: 3-6
  References: `PrismRHI.java:13-38`; `backend/RhiBackendProvider.java:6-12`; `instance/RhiInstanceCreateInfo.java:10-26`; `resource/RhiResource.java:5-11`; NVRHI `8e8c36e.../include/nvrhi/common/resource.h:31-118` (ObjectType/Object/getNativeObject).
  Acceptance criteria: `RhiBackendInjectionTest` and `RhiNativeObjectTest` GREEN; fake provider instance is used by identity even when another provider for the same API exists; unknown native type returns empty; invalidated/closed native object access throws the typed RHI error; `\.\gradlew.bat :prism-rhi-core:test` exits 0.
  QA scenarios: happy: `\.\gradlew.bat :prism-rhi-core:test --tests '*RhiBackendInjectionTest' --tests '*RhiNativeObjectTest'`; failure: pass null provider, duplicate/mismatched object type, and access after invalidation and assert exact exception types. Evidence `<attemptDir>/task-2-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | Prism history style: `explicit backend and native object api`

- [x] 3. Replace implicit shader compilation with manual binary/native shader creation
  What to do / Must NOT do: Introduce `RhiShader`, `RhiShaderDesc(stage, entryPoint, debugName)`, and backend binary formats. `RhiDevice.createShader(desc, format, ByteBuffer)` consumes caller bytes without discovering/choosing a compiler; `adoptShader(desc, nativeObject, ownership, contextIdentity, invalidation)` wraps compatible native modules. Migrate Vulkan, GL41, and GL DSA pipeline stage inputs. Make `ShadercCompiler` an explicitly constructed utility with no service API. Keep transitional adapters only until Todo 6; no runtime fallback from Vulkan GLSL to ServiceLoader.
  Parallelization: Wave 1 | Blocked by: 2 | Blocks: 6, 9-14 | Can parallelize with: 4, 5
  References: `device/RhiDevice.java:29-67`; `shader/RhiShaderModule.java:5-9`; `shader/RhiShaderModuleCreateInfo.java:6-46`; `shader/RhiShaderCompilers.java:14-38`; `pipeline/RhiGraphicsPipelineCreateInfo.java:12-74`; `backend/vulkan/VulkanDevice.java:450-475`; `backend/opengl41/Gl41Device.java:151-160`; `backend/opengldsa/GlDsaDevice.java:135-145`; NVRHI `nvrhi.h:3701-3703`.
  Acceptance criteria: `RhiShaderHandleTest` GREEN for SPIR-V bytes, GL source bytes, native shader adoption, entry point/debug name, copied/read-only byte ownership, and incompatible format/type rejection; shaderc test constructs `new ShadercCompiler(...)`; no executed path invokes `RhiShaderCompilers`.
  QA scenarios: happy: `\.\gradlew.bat :prism-rhi-core:test --tests '*RhiShaderHandleTest' :prism-rhi-shaderc:test`; failure: empty/mutated binary, wrong API object type, wrong context identity, and borrowed handle after invalidation all fail deterministically. Evidence `<attemptDir>/task-3-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `manual shader creation api`

- [x] 4. Make the Vulkan backend adopt a fully initialized external Vulkan context
  What to do / Must NOT do: Add backend-owned `VulkanExternalContext` containing existing LWJGL `VkInstance`, `VkPhysicalDevice`, `VkDevice`, graphics/compute/transfer `VkQueue` plus family indices, enabled extensions/features, context identity/invalidation, optional borrowed `VkSurfaceKHR`, and caller synchronization contract. `VulkanBackendProvider` is constructed with it and never calls `vkCreateInstance`, `vkCreateDevice`, GLFW, or destroys external instance/device/queues/surface. Swapchains/resources created by Prism remain owned by Prism; adopted resources honor `RhiOwnership`.
  Parallelization: Wave 1 | Blocked by: 2 | Blocks: 6, 14, 15-22 | Can parallelize with: 3, 5
  References: `instance/RhiInstanceCreateInfo.java:10-103`; `backend/vulkan/VulkanBackendProvider.java`; `VulkanInstance.java:85-186,388-436`; `VulkanContext.java:69-195`; `VulkanDevice.java:235,305,450-475,547-551`; `VulkanQueue.java`; `VulkanSwapchain.java`; NVRHI `include/nvrhi/vulkan.h:50-84`.
  Acceptance criteria: fixture code outside Prism creates the Vulkan instance/device/queues/surface, passes `new VulkanBackendProvider(externalContext)` to Prism, creates one Prism-owned buffer and one borrowed native object, then closes Prism; debug/deleter probes prove external objects were not destroyed and owned resource was. Production Vulkan source has no GLFW import/call and no Vulkan instance/device creation call.
  QA scenarios: happy: `\.\gradlew.bat :prism-rhi-backend-vulkan:externalContextTest --stacktrace`; failure: missing queue, wrong family, disabled required extension, mismatched context identity, or invalidation during use returns the named `RhiException`. Evidence `<attemptDir>/task-4-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `external vulkan device context`

- [x] 5. Make both OpenGL backends adopt the caller-current context and borrowed GL objects
  What to do / Must NOT do: Add `OpenGlExternalContext(GLCapabilities, ownerThread, contextIdentity, invalidation, currentContextCheck)`. Construct GL41/DSA providers with it; reject calls from the wrong thread/context. Implement borrowed/owned adoption for GL buffer, texture/image/view, sampler, shader, and pipeline object types required by the MC bridge. Test fixture, never Prism production, creates/destroys the hidden GLFW context. Borrowed close releases only wrappers and never calls `glDelete*`.
  Parallelization: Wave 1 | Blocked by: 2 | Blocks: 6, 14, 15-22 | Can parallelize with: 3, 4
  References: `backend/opengl41/Gl41BackendProvider.java`; `Gl41Instance.java:12-39`; `Gl41Support.java:90-102`; `Gl41Device.java` resource close implementations; corresponding `backend/opengldsa` files; `backend/RhiGlStateBridge.java`.
  Acceptance criteria: the same parameterized external-context/native-adoption test passes on GL41 and GL DSA; `glIsBuffer/glIsTexture/glIsShader/glIsProgram` remains true after borrowed wrapper close and becomes false after owned wrapper close; wrong thread/context and stale token fail before a GL call.
  QA scenarios: happy: `\.\gradlew.bat :prism-rhi-backend-opengl41:externalContextTest :prism-rhi-backend-opengl-dsa:externalContextTest`; failure: clear/move the current context and invoke an adopted resource, asserting context error and no deletion. Evidence `<attemptDir>/task-5-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `external opengl context and native adoption`

- [x] 6. Cut PrismRHI 0.1.0, migrate every consumer, remove legacy discovery/window APIs, and publish
  What to do / Must NOT do: Set version 0.1.0. Remove `providers/findProvider`, backend selection from create info, `RhiShaderCompilers`, `RhiContextMode`, `RhiContextCreateInfo`, Vulkan GLFW context implementation, untyped `nativeHandle()`, all four service descriptors, and obsolete dependencies. Update every backend, `VulkanTriangleDemo` (demo creates/owns GLFW/Vulkan context), tests, README, and `docs/zh/*` to the final API. Add architecture/JAR-content tests. Publish all existing publishable modules to a fresh isolated Maven directory and record hashes.
  Parallelization: Wave 1 join | Blocked by: 3, 4, 5 | Blocks: 7-25
  References: `build.gradle.kts:4-14,46-103,106-187`; `README.md:14-105`; `docs/zh/architecture.md`, `backends.md`, `quick-start.md`, `shaders.md`, `triangle-demo.md`; `prism-rhi-demo-triangle/.../VulkanTriangleDemo.java`; four `src/main/resources/META-INF/services/*` files.
  Acceptance criteria: `\.\gradlew.bat clean test publishToMavenLocal -Dmaven.repo.local=<attemptDir>\m2-prism --no-daemon --stacktrace` exits 0; source/JAR scan finds zero ServiceLoader/service descriptors and zero production GLFW init/create/terminate calls; published POMs say 0.1.0; SHA-256 manifest covers every POM/JAR. Copy aggregate output to `D:\Dev\ChenMeng\PrismRHI\.omo\evidence\criterion-1-prism.log` and criterion-2-context.log.
  QA scenarios: happy: run the full command above plus `jar tf` on each artifact; failure: architecture-test fixtures containing ServiceLoader, a service descriptor, legacy method, or GLFW creation are rejected. Evidence `<attemptDir>/task-6-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `0.1.0`

- [ ] 7. Initialize the standalone LuminGraphics Git/Gradle/module topology
  What to do / Must NOT do: Preserve `.omo/`, run `git init -b main`, add Gradle 9.2.1 wrapper/settings/build conventions, Java 17 toolchain, JUnit 5, maven-publish, `mavenLocal { url=<explicit isolated repo> }`, and modules `lumin-graphics-core`, `-render`, `-text`, `-ui`, `-bom`, and non-published `lumin-graphics-demo`. Pin PrismRHI 0.1.0 GAVs; use no composite/project dependency. Add `.gitignore` for builds/caches/runtime evidence while retaining plans/docs.
  Parallelization: Wave 2 | Blocked by: 6 | Blocks: 8-14
  References: empty `D:\Dev\ChenMeng\LuminGraphics`; Prism `settings.gradle.kts` and `build.gradle.kts:46-103` for Java/publishing conventions; isolated Prism Maven manifest from Todo 6.
  Acceptance criteria: pre-edit `\.\gradlew.bat test` RED is captured as missing wrapper; post-edit `\.\gradlew.bat clean test -Dmaven.repo.local=<attemptDir>\m2-prism` exits 0; `dependencyInsight` resolves `prism-rhi-core:0.1.0` from the isolated repository; published module dependency graph is exactly `ui -> text -> render -> core -> Prism`, with demo outside publications.
  QA scenarios: happy: run root `projects`, `test`, and `:lumin-graphics-core:dependencyInsight --dependency prism-rhi-core --configuration compileClasspath`; failure: remove/point the isolated repo to empty and assert dependency resolution fails rather than silently using a project/global mavenLocal artifact. Evidence `<attemptDir>/task-7-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `build: initialize standalone modules`

- [ ] 8. Lock the 53-source and 41-resource migration contracts
  What to do / Must NOT do: Create `docs/migration/epsilon-surface.csv` with exactly 53 unique source rows and `docs/resources/manifest.csv` with 37 shader + 4 font rows. Deterministic destinations: graphics root/buffer/immediate/renderers/schedulers/shaders -> core/render; `graphics/text/**` except `text/minecraft/**` -> text; all `gui/lib/**` -> ui; three `graphics/text/minecraft/**` -> LuminGraphics-MC version-common adapters. Replace `StaticFontLoader` settings/holder coupling with text `FontRegistry`; replace LuminRenderSystem/Minecraft globals with injected core protocols. Record preserved behavior and test id for every row; use `assets/lumin_graphics/` targets and SHA-256/provenance for every resource.
  Parallelization: Wave 2 | Blocked by: 7 | Blocks: 13, 14, 23-25 | Can parallelize with: 9 after package/module names are fixed
  References: sorted file inventories under `Epsilon-Private/common/src/main/java/com/github/epsilon/graphics` (41) and `.../gui/lib` (12); resource inventories under `assets/epsilon/shaders` (37) and `fonts` (4); representative couplings `LuminRenderSystem.java:3-22`, `Render2DScheduler.java:27-295`, `UiTree.java:23-1185`, `LuminUiRenderer.java:18-479`.
  Acceptance criteria: `MigrationManifestTest` reads the actual source tree and asserts a bijection of 41+12=53, valid target module/symbol, nonempty behavior/test id, and exactly three MC destinations; `ResourceManifestTest` asserts 37+4, SHA matches, no duplicate/epsilon target namespace, and provenance field. A mutation deleting or duplicating any row makes the named test RED.
  QA scenarios: happy: `\.\gradlew.bat :lumin-graphics-core:test --tests '*MigrationManifestTest' --tests '*ResourceManifestTest'`; failure: test fixture omits one source and changes one SHA, and both exact assertions fail. Evidence `<attemptDir>/task-8-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `docs: lock epsilon migration surface`

- [ ] 9. Implement LuminGraphics core protocols, geometry, resource ownership, and fake-RHI testkit
  What to do / Must NOT do: Under `com.github.slmpc.lumingraphics`, implement injected `LuminGraphicsContext` (RhiDevice, render-thread executor/check, viewport/scale metrics, render-target supplier, resource registry), immutable geometry/color/viewport/scissor types, vertex formats, texture facade, invalidation/lifetime rules, and a test-fixture fake RHI that records commands/native ownership. No static Minecraft singleton, window API, or generic framework beyond boundaries demanded by the ledger.
  Parallelization: Wave 2 | Blocked by: 7 | Blocks: 10-14 | Can parallelize with: 8
  References: Epsilon `graphics/LuminRenderSystem.java:31-339`, `LuminTexture.java`, `LuminVertexFormats.java`, `buffer/BufferUtils.java`, `gui/lib/UiRect.java`, `UiTextMetrics.java`, `UiTheme.java`, `state/UiInvalidationState.java`; Prism final ownership/native API from Todos 2-6.
  Acceptance criteria: lifecycle tests prove render-thread enforcement, viewport/scissor coordinate conversions, owned resources close once, borrowed resources are not closed, invalidated resources fail, and fake RHI produces stable traces; ArchUnit reports no forbidden packages in published modules.
  QA scenarios: happy: `\.\gradlew.bat :lumin-graphics-core:test`; failure: invoke on wrong thread, close twice, use after invalidation, and feed negative/out-of-bounds geometry, asserting typed outcomes without GPU calls. Evidence `<attemptDir>/task-9-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `feat(core): add graphics context and resource contracts`

- [ ] 10. Port and precompile the complete shader/pipeline resource set for all Prism backends
  What to do / Must NOT do: Move all 37 shader sources to `lumin-graphics-render/src/main/resources/assets/lumin_graphics/shaders`. Replace nine `#moj_import` vertex shaders with an explicit ABI: `LuminFrame` projection/viewport uniform block, per-draw transform/color/UV attributes, declared samplers, `main` entry. Add build-time explicit Shaderc invocation producing SPIR-V resources and retain GLSL bytes for GL41/DSA. Recreate pipeline descriptors for rectangle, TTF AA/no-AA, round rect/outline, shadow/segmented shadow, texture, triangle, blur/filter/FXAA/menu effects. No runtime compiler discovery.
  Parallelization: Wave 2 | Blocked by: 9 | Blocks: 11-14 | Can parallelize with: 12
  References: Epsilon `LuminRenderPipelines.java:13-87`, `LuminVertexFormats.java`; all 37 `assets/epsilon/shaders/**`; `BlurShader.java`, `FilterShader.java`, `FXAAShader.java`, `GlslSandBox.java`; nine `#moj_import` files identified in the draft findings; Prism manual shader contract from Todo 3.
  Acceptance criteria: `ShaderArtifactTest` maps all 37 manifest rows, rejects `#moj_import`, validates SPIR-V magic and entry point, and checks descriptor ABI; `shaderCompileTest` compiles every stage with explicit shaderc and validates GL source; no shader file is merely copied without a pipeline/effect mapping or documented library-only role.
  QA scenarios: happy: `\.\gradlew.bat :lumin-graphics-render:shaderCompileTest :lumin-graphics-render:test --tests '*ShaderArtifactTest'`; failure: mutate a uniform/attribute name and corrupt one SPIR-V byte, asserting ABI and binary validation failures. Evidence `<attemptDir>/task-10-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `feat(render): port backend-neutral shaders`

- [ ] 11. Rebuild immediate rendering, 2D/3D schedulers, renderers, and effects on PrismRHI
  What to do / Must NOT do: Implement ring-buffer allocation/rotation, immediate vertex batches, rectangle/round/outline/shadow/text/texture/triangle renderers, layer command records, bounds/quadtree/scissor/texture references, deterministic `Render2DScheduler`, and `Render3DScheduler`. Replace Mojang `RenderPass`/Gpu* calls with Prism command buffers/descriptors/pipelines; replace RendererHolder/ScissorUtils/ClientSetting with injected context/services. Preserve clear/flush/flushAndClear/end-frame semantics and command order.
  Parallelization: Wave 2 | Blocked by: 9, 10 | Blocks: 13, 14 | Can parallelize with: 12
  References: Epsilon `buffer/LuminRingBuffer.java`; `immediate/LuminImmediateRenderer.java`, `LuminTessellator.java`; renderer files `IRenderer`, `RectRenderer`, `RoundRectRenderer`, `RoundRectOutlineRenderer`, `ShadowRenderer`, `TextRenderer`, `TextureRenderer`, `TriangleRenderer`; `schedulers/render2d/*`; `schedulers/render3d/Render3DScheduler.java`; shader effect classes.
  Acceptance criteria: fake-RHI golden tests cover layer sorting, stable order within layer, culling/bounds, nested scissor intersection, empty flush, texture binding, ring wrap/no-overwrite, 2D clear rules, 3D priority, and exception cleanup. Every relevant migration row points to a passing test.
  QA scenarios: happy: `\.\gradlew.bat :lumin-graphics-render:test`; failure: overflow ring capacity, inverted scissor, close during frame, duplicate flush, missing texture/shader, and backend error all produce the specified exception/cleanup trace. Evidence `<attemptDir>/task-11-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `feat(render): rebuild renderers and schedulers`

- [ ] 12. Port TTF, glyph atlas, text rendering, icons, and emoji without Minecraft
  What to do / Must NOT do: Implement `FontResource`/path loaders, STB font parsing, async glyph request/revision logic, atlas packing/upload through Lumin/Prism textures, text measurement/rendering, `FontRegistry`, `IconChars`, and AWT emoji rasterization. Preserve four fonts byte-identically for local-only Maven use, record SHA/provenance, and make release redistribution a documented later gate. Do not include the three Minecraft font adapter classes here.
  Parallelization: Wave 2 | Blocked by: 9, 10 | Blocks: 13, 14 | Can parallelize with: 11
  References: Epsilon `graphics/text/IFontLoader.java`, `ITextRenderer.java`, `GlyphDescriptor.java`, `IconChars.java`, `StaticFontLoader.java`, `SystemEmojiAtlas.java`; `graphics/text/ttf/{TtfFontFile,TtfFontLoader,TtfGlyph,TtfGlyphAtlas,TtfTextRenderer}.java`; four `assets/epsilon/fonts/*.ttf`.
  Acceptance criteria: tests open all four fonts with STB, verify glyph metrics/kerning/scale consistency, atlas growth/revision/cache invalidation, concurrent request deduplication, deterministic text layout, emoji fallback, upload ownership and full close; resource manifest SHA remains exact.
  QA scenarios: happy: `\.\gradlew.bat :lumin-graphics-text:test`; failure: malformed font, missing glyph, cancelled async load, atlas exhaustion, upload failure, and use-after-close yield deterministic typed errors and no leaked Prism resource. Evidence `<attemptDir>/task-12-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `feat(text): port font and glyph pipeline`

- [ ] 13. Rebuild the declarative UI tree, scene/batch lifecycle, renderer, and controls
  What to do / Must NOT do: Split the 1185-line `UiTree` into public node records/sealed interfaces, builder scopes, layout helpers, and interaction descriptors under `com.github.slmpc.lumingraphics.ui`; implement theme/text metrics/rect/state, layer stack/scene, render batch/content viewport, Prism-backed `LuminUiRenderer`, and scrollbar. Preserve every node kind (layer, scissor, shadow, gradients, rect/text/texture, button/switch/input/assist-chip/segmented/icon-button/popup/slider/triangle/viewport) without copying Epsilon animation/settings dependencies; accept injected animation clocks and resource ids.
  Parallelization: Wave 2 | Blocked by: 8, 11, 12 | Blocks: 14
  References: Epsilon `gui/lib/UiTree.java:23-1185`; `UiTheme.java`, `UiTextMetrics.java`, `UiRect.java`, `state/UiInvalidationState.java`; `scene/{UiScene,UiLayer,UiLayerStack}.java`; `render/{UiRenderBatch,UiContentBuffer,LuminUiRenderer}.java`; `control/UiScrollBar.java`; `graphics/schedulers/render2d/*`.
  Acceptance criteria: UI contract/golden tests cover each node variant, linear/layout nesting, content clipping, nested scissor, six layer values, popup ordering, text measurement parity, invalidation signatures, animation-dirty propagation, viewport/marquee, batch begin/end pairing, empty tree, and renderer cleanup after failure. All 12 gui.lib ledger rows point to passing tests.
  QA scenarios: happy: `\.\gradlew.bat :lumin-graphics-ui:test`; failure: malformed tree, unbalanced scene frame, invalid dimensions, missing texture/font, nested scissor outside parent, and renderer exception preserve state and emit typed errors. Evidence `<attemptDir>/task-13-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `feat(ui): rebuild declarative ui pipeline`

- [ ] 14. Prove standalone rendering on GL41, GL DSA, and Vulkan, then publish LuminGraphics 0.1.0
  What to do / Must NOT do: Implement `lumin-graphics-demo` as the user side: it creates/owns a hidden GLFW window and GL or Vulkan context, explicitly constructs the matching provider, passes it to Prism/Lumin, renders a deterministic scene using geometry/text/UI/effect pipelines, reads pixels, and tears down in reverse order. The four publishable modules never see GLFW. Publish BOM/modules to `<attemptDir>\m2-chain`, record POM/JAR/resource SHA, and prove all dependencies came from Todo 6's Prism artifact.
  Parallelization: Wave 2 join | Blocked by: 8-13 | Blocks: 15-25
  References: final Prism demo from Todo 6; all Lumin modules; Epsilon frame lifecycle `MixinRenderSystem.java:13-20`, `LuminRenderSystem.beginFrame/endFrame`, `UiScene.java:16-74`; shader/pipeline resources from Todo 10.
  Acceptance criteria: `gl41Smoke`, `glDsaSmoke`, and `vulkanSmoke` each exit 0, compile/link every relevant pipeline, render the same canonical scene within backend-specific tolerance, and produce a nonblank readback PNG plus JSON hash/metrics; wrong-context negative smoke fails before draw. `publishToMavenLocal` writes only 0.1.0 artifacts, and `jar tf` proves all 37 shaders/4 fonts are present exactly once. Copy aggregate test output to `D:\Dev\ChenMeng\LuminGraphics\.omo\evidence\criterion-3-lumin-tests.log`.
  QA scenarios: happy: `\.\gradlew.bat clean test gl41Smoke glDsaSmoke vulkanSmoke publishToMavenLocal -Dmaven.repo.local=<attemptDir>\m2-chain --stacktrace`; failure: run `wrongContextSmoke` and a missing-shader fixture, asserting deterministic nonzero/error and complete fixture-owned window/context cleanup. Evidence `<attemptDir>/task-14-lumin-graphics-prism-rhi-migration.log` plus PNG/JSON artifacts.
  Commit: Y | `feat: publish standalone 0.1.0`

- [ ] 15. Initialize LuminGraphics-MC and prove both exact Minecraft source/toolchain baselines
  What to do / Must NOT do: In `D:\Dev\ChenMeng\LuminGraphics-MC`, run `git init -b main`; add Gradle 9.2.1/JDK25 root with modules `bridge-contract`, `mc-26.1.2-common|fabric|neoforge`, and `mc-26.2-common|fabric|neoforge`. Pin the exact versions in Scope, configure Mojang official mappings for Fabric and NeoForm for common/NeoForge, isolated Maven resolution for Prism/Lumin 0.1.0, and root `buildAllVariants`. Before any 26.2 accessor code, run ModDev/Loom source-generation tasks for both versions, extract sources under `reference/vanilla-26.1.2` and `reference/vanilla-26.2`, and record SHA/origin for required Blaze3D classes.
  Parallelization: Wave 3 | Blocked by: 14 | Blocks: 16-22
  References: Epsilon `settings.gradle.kts:1-44`, `gradle/libs.versions.toml:3-11`, `common/build.gradle.kts:1-78`, `fabric/build.gradle.kts:1-47`, `neoforge/build.gradle.kts:1-83`, `gradle/wrapper/gradle-wrapper.properties`; official metadata findings in the approved draft.
  Acceptance criteria: pre-edit `\.\gradlew.bat buildAllVariants` RED is captured as missing wrapper; post-edit `projects` lists all seven modules; `downloadMinecraftSources` produces both version trees; a source-baseline test verifies package/class/field/method signatures for GpuBuffer/GlBuffer, GpuTexture/GlTexture/View, GlShaderModule, GlProgram/Pipeline, CommandEncoder/RenderPass, GlDevice, and RenderSystem separately per version. No 26.2 signature is copied from 26.1.2 without matching evidence.
  QA scenarios: happy: `\.\gradlew.bat projects downloadMinecraftSources verifyMinecraftSources -Dmaven.repo.local=<attemptDir>\m2-chain`; failure: point the 26.2 verifier at the 26.1.2 manifest and assert version/hash/signature mismatch. Evidence `<attemptDir>/task-15-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `build: initialize minecraft version matrix`

- [ ] 16. Define the shared bridge contract and complete per-object/per-direction matrix
  What to do / Must NOT do: In no-Minecraft `bridge-contract`, implement `BridgeMode`, `BridgeDirection`, `BridgeOwnership`, `BridgeContextIdentity`, `BridgeInvalidationToken`, `BridgeCapability`, `BridgeResult<T>`/`BridgeLease<T>`, and typed unsupported reasons. Add machine-readable `docs/bridge-matrix.csv` with version, loader, backend, object, direction, minimum mode, accessor, thread/context rule, owner, invalidation, close behavior, and test id. Minimums: texture/buffer/shader both directions borrowed-zero-copy; sampler/pipeline rebuilt; encoder/pass in-place-adapter; unknown/Vulkan unsupported.
  Parallelization: Wave 3 | Blocked by: 15 | Blocks: 17-22
  References: approved decision B; 26.1.2 `GlTexture.java:12-89`, `GlBuffer.java:13-49`, `GlShaderModule.java:10-42`, `GlProgram.java:22-163`, `GlCommandEncoder.java:43-72,156-203`, `RenderPass.java:19-125`; 26.2 source manifest from Todo 15; Prism ownership/native contract.
  Acceptance criteria: contract tests enforce exhaustive modes/reasons, idempotent lease close, borrowed non-destruction, owner invalidation, wrong context/thread, and matrix schema. Matrix has both versions, both directions, and each listed object exactly once per applicable loader/backend; mutation of any mode/owner/test id fails.
  QA scenarios: happy: `\.\gradlew.bat :bridge-contract:test`; failure: construct a lease with mismatched context, double close, use after invalidation, or an incomplete matrix row and assert the named error. Evidence `<attemptDir>/task-16-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `feat(bridge): define ownership and capability contract`

- [ ] 17. Implement the 26.1.2 Blaze3D common bridge and borrowed GL wrapper mechanics
  What to do / Must NOT do: Against the verified 26.1.2 source only, implement `Blaze3DBridge2612` for GpuTexture/View, GpuBuffer/Slice, GlShaderModule, sampler, logical/compiled pipeline, CommandEncoder, and RenderPass. Use Mixin duck interfaces/constructor invokers and loader access rules to read internal handles and tag borrowed wrappers. Borrowed GlBuffer/GlTexture/GlShaderModule/GlProgram close interceptors must release wrapper bookkeeping but skip `glDelete*`; original objects keep normal close. Translate format/usage/vertex/uniform metadata and reject cross-context objects before access.
  Parallelization: Wave 3 | Blocked by: 16 | Blocks: 19-22 | Can parallelize with: 18
  References: `reference/vanilla-26.1.2/com/mojang/blaze3d/buffers/GpuBuffer.java:12-65`; `opengl/GlBuffer.java:13-49`; `textures/GpuTexture.java:11-68`; `GlTexture.java:12-89`; `GlTextureView.java:12-71`; `GlShaderModule.java:10-42`; `GlProgram.java:22-163`; `GlRenderPipeline.java:8-13`; `GlDevice.java:47-103,118-220,291-307`; `GlCommandEncoder.java`; `systems/RenderPass.java`; three Epsilon Minecraft text adapters.
  Acceptance criteria: version-common unit/bytecode tests prove all matrix paths, constructor/accessor targets, borrowed flags, usage/format translation, text glyph/metrics/renderable adapters, and no reflection. Unsupported encoder/pass conversion returns an in-place adapter, never a fake native handle.
  QA scenarios: happy: `\.\gradlew.bat :mc-26.1.2-common:test verify2612AccessTargets`; failure: wrong GL object subtype/context, closed source, incompatible usage/format, stale pipeline cache, and missing accessor signature yield typed unsupported/drift errors. Evidence `<attemptDir>/task-17-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `feat(mc-26.1.2): implement blaze3d bridge`

- [ ] 18. Implement the independently verified 26.2 Blaze3D common bridge
  What to do / Must NOT do: Repeat Todo 17 from the 26.2 source manifest, adapting exact class/field/method differences in the `mc-26.2-common` source tree rather than sharing version-bound casts/accessors. Keep only bridge-contract/value translation helpers shared. Update matrix rows with actual 26.2 access targets and SHA. Fail closed when an expected target changed.
  Parallelization: Wave 3 | Blocked by: 15, 16 | Blocks: 19-22 | Can parallelize with: 17
  References: `reference/vanilla-26.2/**` and signature manifest produced by Todo 15; Todo 17 only as behavioral reference, never as source truth; official 26.2 GAV pins in Scope.
  Acceptance criteria: `mc-26.2-common` tests pass against 26.2 only; a cross-version source-set test proves no `reference/vanilla-26.1.2`, 26.1.2 generated source, or 26.1.2 accessor class is on its compile/runtime classpath; all bridge-matrix minimums are met or explicitly fail the task rather than silently downgrade.
  QA scenarios: happy: `\.\gradlew.bat :mc-26.2-common:test verify262AccessTargets verifyNoCrossVersionLeak`; failure: substitute one 26.1.2 class/manifest and assert compile/signature/source-leak failure. Evidence `<attemptDir>/task-18-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `feat(mc-26.2): implement blaze3d bridge`

- [ ] 19. Wire Fabric 26.1.2 and 26.2 dependency-mod entry points, Access Wideners, and Mixins
  What to do / Must NOT do: Add both Fabric modules with Loom, client entry point, `fabric.mod.json`, version-specific access widener, mixin config/accessors, common-output consumption, and isolated Maven dependencies. Register render-thread bridge initialization/disposal without creating a window/context. Use stable mod id and exact MC/version constraints; do not place Fabric types in version-common or bridge-contract.
  Parallelization: Wave 3 | Blocked by: 17, 18 | Blocks: 21, 22 | Can parallelize with: 20
  References: Epsilon `fabric/build.gradle.kts:1-47`, `fabric/src/main/resources/fabric.mod.json`, access widener/mixin resources; Todo 15 source manifests and Todos 17/18 accessor needs.
  Acceptance criteria: both Fabric `clean build` tasks remap successfully; access-widener validation proves every target exists in the matching version; metadata declares `lumin_graphics_mc`, exact Minecraft range and Java 25; loader-boundary test finds no Fabric type elsewhere.
  QA scenarios: happy: `\.\gradlew.bat :mc-26.1.2-fabric:clean :mc-26.1.2-fabric:build :mc-26.2-fabric:clean :mc-26.2-fabric:build`; failure: swap the two access wideners and assert target/version validation fails. Evidence `<attemptDir>/task-19-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `feat(fabric): wire both minecraft versions`

- [ ] 20. Wire NeoForge 26.1.2 and 26.2 dependency-mod entry points, Access Transformers, and Mixins
  What to do / Must NOT do: Add both NeoForge modules with ModDevGradle, client mod entry point, `META-INF/neoforge.mods.toml`, version-specific access transformer, mixin configs/accessors, common-output consumption, and isolated Maven dependencies. Register the same lifecycle contract as Fabric without loader leakage or context/window creation.
  Parallelization: Wave 3 | Blocked by: 17, 18 | Blocks: 21, 22 | Can parallelize with: 19
  References: Epsilon `neoforge/build.gradle.kts:1-83`, `neoforge/src/main/resources/META-INF/neoforge.mods.toml`, mixin resources, common AT configuration; Todo 15 manifests and bridge access requirements.
  Acceptance criteria: both NeoForge `clean build` tasks reobfuscate successfully; AT validation proves exact-version targets; metadata declares stable id/version constraints/Java 25; loader-boundary test finds no NeoForge type elsewhere.
  QA scenarios: happy: `\.\gradlew.bat :mc-26.1.2-neoforge:clean :mc-26.1.2-neoforge:build :mc-26.2-neoforge:clean :mc-26.2-neoforge:build`; failure: swap AT files or remove a mixin registration and assert validation/startup-contract test fails. Evidence `<attemptDir>/task-20-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | `feat(neoforge): wire both minecraft versions`

- [ ] 21. Package four self-contained dependency-mod artifacts and the aggregate matrix task
  What to do / Must NOT do: Configure Fabric include/JIJ and NeoForge jarJar so each loader artifact contains Lumin core/render/text/ui and Prism core + GL41 + GL DSA 0.1.0 exactly once, but not LWJGL/Minecraft/loader jars or shaderc. Exact artifact ids: `lumin-graphics-mc-fabric-26.1.2`, `...-neoforge-26.1.2`, `...-fabric-26.2`, `...-neoforge-26.2`, all Maven version 0.1.0; mod metadata version is `0.1.0+mc<version>`. Root `buildAllVariants` depends on four clean remap/reobf build outputs and verification.
  Parallelization: Wave 3 | Blocked by: 19, 20 | Blocks: 22-25
  References: GAVs in Scope; Prism/Lumin isolated manifests from Todos 6/14; loader packaging conventions in Epsilon builds; module graph from Todo 15.
  Acceptance criteria: `buildAllVariants` exits 0; JAR inspection verifies names, metadata, accessors/mixins, Lumin assets/classes, Prism classes, no duplicate paths, no services, no forbidden bundled dependencies; dependencyInsight for each common module shows only `<attemptDir>\m2-chain` Prism/Lumin artifacts and matching SHA.
  QA scenarios: happy: `\.\gradlew.bat clean buildAllVariants verifyVariantJars -Dmaven.repo.local=<attemptDir>\m2-chain --stacktrace`; failure: inject a stale 0.0.1 Prism or wrong-MC common artifact fixture and assert dependency/hash/metadata verification fails. Evidence `<attemptDir>/task-21-lumin-graphics-prism-rhi-migration.log` and `D:\Dev\ChenMeng\LuminGraphics-MC\.omo\evidence\criterion-4-matrix.log`.
  Commit: Y | `build: package all dependency mod variants`

- [ ] 22. Run four self-terminating real Minecraft client bridge smokes
  What to do / Must NOT do: Add a test-only smoke mode to each loader/version entry point. On the real Minecraft render thread/current GL context it creates source texture/buffer/shader objects in Blaze and Prism, exercises both bridge directions for texture/buffer/shader, rebuilds sampler/pipeline, uses the command/pass adapter, closes borrowed wrappers, verifies GL names still exist, then closes owners and verifies deletion. Render a deterministic pixel, write JSON/PNG, request normal client shutdown, and remove all runtime dirs/processes. Run variants sequentially to avoid GL/user-dir contention.
  Parallelization: Wave 3 join | Blocked by: 21 | Blocks: 23-25
  References: Todo 16 bridge matrix; per-version common/loader code; `RenderSystem.java:82-101` thread checks; `GlCommandEncoder.java` casts/lifecycle; `GlBuffer.close`, `GlTexture.close`, `GlShaderModule.close`, `GlProgram.close`.
  Acceptance criteria: `runAllBridgeSmokes` exits 0 and produces four JSON files with version/loader/context identity, each required object+direction/mode, pre/post-close `glIs*`, pixel hash, cleanup status, and `pass=true`; four PNGs are nonblank; no client/Java process or run lock remains. Negative modes prove wrong-thread, wrong-context, stale token, and missing accessor fail before deletion/draw.
  QA scenarios: happy: PowerShell `\.\gradlew.bat runAllBridgeSmokes -Dmaven.repo.local=<attemptDir>\m2-chain --stacktrace`; failure: `\.\gradlew.bat runAllBridgeNegativeSmokes` and assert four expected typed failures plus clean automatic exit. Evidence `<attemptDir>/task-22-lumin-graphics-prism-rhi-migration.log`, four JSONs, four PNGs, and cleanup receipt.
  Commit: Y | `test: add real bridge smoke clients`

- [ ] 23. Generate hierarchical AGENTS.md and complete linked architecture/API/build documentation
  What to do / Must NOT do: Run the `init-deep` workflow after structures stabilize, with at most four subagents plus root. Create/update root and only complexity-justified module AGENTS.md files without parent duplication. Complete Prism docs for 0.1.0 provider/context/native/shader ownership; Lumin docs for modules/API/render/shader/text/UI/demo/migration/resources; MC docs for bridge matrix/access mechanics/version matrix/packaging/consumer setup/smoke tests. Cross-link all docs from each README and record local-Maven-only release status.
  Parallelization: Wave 4 | Blocked by: 6, 14, 22 | Blocks: 25 | Can parallelize with: 24
  References: Epsilon `AGENTS.md` source-first/render-thread constraints and `docs/README.md`; approved draft; all final module layouts; `docs/migration/epsilon-surface.csv`, `docs/resources/manifest.csv`, `docs/bridge-matrix.csv`.
  Acceptance criteria: init-deep scoring receipts justify each AGENTS location; root files are 50-150 lines and child files 30-80 without generic duplication; documentation link checker, code sample compilation, CSV validators, and stale-name scan pass; docs cover every public contract and command in Success criteria.
  QA scenarios: happy: run repository documentation/link/sample validators plus `git diff --check`; failure: fixture broken link, stale `PrismRHI.createInstance(createInfo)`, duplicate AGENTS rule, or undocumented public package fails. Evidence `<attemptDir>/task-23-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y in each repo | Prism style `0.1.0 documentation`; new repos `docs: document architecture and workflows`

- [ ] 24. Enforce cross-repository architecture, scope, version, and anti-reflection guards
  What to do / Must NOT do: Add machine-executable tests/scripts that inspect compiled dependency graphs, source/import AST or class constant pools, and built JARs. Enforce: no forbidden Minecraft/Epsilon/window deps in Lumin; no ServiceLoader/services/production GLFW creation in Prism; no reflection and no loader type outside loader modules in MC; no version cross-leak; one-way published GAV graph; 53/37/4 ledgers complete; bridge matrix exhaustive; public package namespace only the approved names.
  Parallelization: Wave 4 | Blocked by: 6, 14, 22 | Blocks: 25 | Can parallelize with: 23
  References: Scope guardrails; all Gradle build files/JARs; Metis P1 architecture findings; migration/resource/bridge manifests.
  Acceptance criteria: `architectureCheck` tasks in all three repos exit 0 on the final tree and prove they inspected nonzero source/class/JAR entries; mutation fixtures for each forbidden category go RED with the exact offending file/class/dependency.
  QA scenarios: happy: run Prism `architectureCheck`, Lumin `architectureCheck`, and MC root `architectureCheck`; failure: execute all mutation fixtures (ServiceLoader, Minecraft import in Lumin, reflection in bridge, Fabric type in common, 26.1.2 class in 26.2, stale GAV) and assert rejection. Evidence `<attemptDir>/task-24-lumin-graphics-prism-rhi-migration.log`.
  Commit: Y | per repo: `test: enforce architecture boundaries`

- [ ] 25. Reproduce the complete isolated Maven/build/runtime chain and leave atomic clean histories
  What to do / Must NOT do: Create a fresh attempt-owned Maven directory; build/test/publish Prism, then resolve/build/test/publish Lumin, then build all four MC variants and run all client smokes using only that directory. Recompute POM/JAR/PNG/JSON hashes, run dependencyInsight, full tests, architecture/docs checks, `git diff --check`, status/log in all three repos, and verify every todo evidence/cleanup receipt. Fix only criterion-proven failures and rerun affected scenarios before one final full pass.
  Parallelization: Wave 4 join | Blocked by: 23, 24 | Blocks: F1-F4
  References: all prior todo artifacts; active goal criteria; each repository build/docs/AGENTS; local Git message history (Prism plain lower-case subjects; new repos Conventional Commits).
  Acceptance criteria: exact commands in Success criteria all exit 0 from clean checkouts/state; artifact hashes match downstream dependency hashes; four client smokes pass; all three `git status --porcelain` are empty except explicitly gitignored evidence; commit logs show revertable increments with no WIP/omnibus commit; temp Maven/runtime/window/process resources are deleted and receipts recorded.
  QA scenarios: happy: execute `scripts/verify-all.ps1 -MavenRepo <attemptDir>\m2-final -EvidenceDir <attemptDir>\final` from Lumin plan tooling; failure: rerun its built-in stale-artifact and missing-evidence probes and assert nonzero before cleanup. Evidence `<attemptDir>/task-25-lumin-graphics-prism-rhi-migration.log`, final artifact manifest, and cleanup receipt.
  Commit: N | verification-only; any necessary fix belongs to its owning prior atomic scope

## Final verification wave
> Runs in parallel after ALL todos. ALL must APPROVE. Surface results and wait for the user's explicit okay before declaring complete.
- [ ] F1. Plan compliance audit
  Reviewer: read-only rigorous reviewer, independent of implementers. Verify every Todo 1-25 acceptance criterion against the actual command, artifact, RED/GREEN log, cleanup receipt, and commit; reject self-report, grep-only runtime claims, skipped tests, stale evidence, or missing fixed-path criterion logs. Confirm B/A/A and the later LuminGraphics-MC split override the older goal wording. Evidence `<attemptDir>/final-F1-plan-compliance.md`. Verdict must be unconditional `APPROVE`.
- [ ] F2. Code quality, ownership, and security review
  Reviewer: read-only code-quality/security reviewer. Inspect full diffs and public APIs for typed ownership/context/invalidation correctness, borrowed deletion hazards, GL/Vulkan thread safety, buffer bounds, resource leaks, exception cleanup, unsafe native access, shader/resource validation, access-transform blast radius, and API/doc coherence. Run focused tests for every finding before blocking. Evidence `<attemptDir>/final-F2-code-quality.md`. Verdict must be unconditional `APPROVE`.
- [ ] F3. Real manual QA
  Reviewer/executor: run the real Prism external GL/Vulkan fixtures, the standalone Lumin deterministic demo on all three backends, and the four self-terminating Minecraft clients. Inspect PNG pixels (nonblank and expected pattern), JSON bridge results, window/process cleanup, and artifact resolution. Do not substitute unit tests or log-only boot. Evidence `<attemptDir>/final-F3-manual-qa.md` plus screenshots/JSON/action log/cleanup receipt. Verdict must be unconditional `APPROVE`.
- [ ] F4. Scope fidelity and migration completeness
  Reviewer: independently compare the actual 41 graphics + 12 gui.lib sources, 37 shaders, and 4 fonts against the ledgers and destination code/tests; verify no Epsilon business code, compatibility facade, Minecraft leakage into Lumin, legacy Prism API, reflection, or fake Vulkan bridge entered scope. Check all four version/loader artifacts and docs/AGENTS. Evidence `<attemptDir>/final-F4-scope-fidelity.md`. Verdict must be unconditional `APPROVE`.

## Commit strategy
- PrismRHI follows its existing short plain-English style: `explicit backend and native object api`, `manual shader creation api`, `external vulkan device context`, `external opengl context and native adoption`, `0.1.0`, `0.1.0 documentation`, `test architecture boundaries`. Before each commit run the owning task tests and inspect staged diff/stat; no service/window legacy survives the 0.1.0 commit.
- LuminGraphics is a new repository and uses Conventional Commits: build scaffold, migration ledger, core, render shaders, render schedulers, text, UI, published demo, docs, architecture tests. The first commit includes the approved `.omo` plan/draft but excludes evidence/build caches. Final commit footer: `Plan: .omo/plans/lumin-graphics-prism-rhi-migration.md`.
- LuminGraphics-MC is a new repository and uses Conventional Commits: matrix scaffold/source manifests, bridge contract, 26.1.2 bridge, 26.2 bridge, Fabric wiring, NeoForge wiring, packaging, real client smokes, docs, architecture tests. Exact-version accessor changes stay with their version bridge/loader test commit.
- Never commit RED-only state, WIP, generated run directories, global Maven cache contents, screenshots/evidence unless the repo explicitly tracks evidence, or unrelated user changes. Each commit must build/test green and be independently revertable.
- Before composing each commit, run `git log --oneline -20` and `git log -5 -- <paths>` (where history exists), `git diff --staged --stat`, staged diff review, then `git log -1 --oneline` after commit.

## Success criteria
1. **Prism explicit backend/native/shader:** `D:\Dev\ChenMeng\PrismRHI\gradlew.bat clean test publishToMavenLocal -Dmaven.repo.local=<fresh-m2>` exits 0. Tests named `RhiBackendInjectionTest`, `RhiNativeObjectTest`, and `RhiShaderHandleTest` have captured pre-implementation RED and final GREEN. Published 0.1.0 JARs contain no ServiceLoader descriptors/API, accept explicitly constructed Vulkan/GL providers, accept caller shader bytes/native objects, and expose typed native-object lookup.
2. **External-only context/window ownership:** `ExternalContextOwnershipTest` and three real backend external-context tasks exit 0; production Prism contains no GLFW/SDL window/context/surface creation/destruction, Vulkan instance/device creation, or ownership of caller instance/device/queue/surface/context. Borrowed close and invalidation/thread/context adversarial cases are proven.
3. **Standalone Lumin extraction:** `D:\Dev\ChenMeng\LuminGraphics\gradlew.bat clean test gl41Smoke glDsaSmoke vulkanSmoke publishToMavenLocal -Dmaven.repo.local=<fresh-m2>` exits 0. The verified ledger maps all 41 graphics + 12 gui.lib files, all behavior groups have tests, all 37 shaders compile/use the new ABI, all 4 fonts load, rendered PNGs are nonblank, and published modules have no Minecraft/Epsilon/window dependencies.
4. **Separate MC four-variant matrix and bridges:** `D:\Dev\ChenMeng\LuminGraphics-MC\gradlew.bat clean buildAllVariants runAllBridgeSmokes -Dmaven.repo.local=<fresh-m2>` exits 0. Fabric and NeoForge 26.1.2/26.2 artifacts use exact pins, accessors validate against exact source, both bridge directions meet the matrix, four real clients pass/exit/clean up, and no stale/project/composite Prism/Lumin dependency is resolved.
5. **Repositories, docs, knowledge, and artifacts:** LuminGraphics and LuminGraphics-MC are initialized Git repositories; all three repositories have clean status, atomic histories, `git diff --check` clean, justified AGENTS hierarchy, linked docs, passing architecture/link/sample checks, Maven POM/JAR SHA manifests, and no unrelated modifications to Epsilon-Private.
6. **Evidence/review stop gate:** every Todo and F1-F4 has its named evidence and cleanup receipt; fixed goal evidence logs exist (with the corrected MC matrix location); F1-F4 all return unconditional `APPROVE`; one final full verification pass succeeds from a fresh isolated Maven directory. Stop immediately after the user acknowledges the surfaced final-verification results.
