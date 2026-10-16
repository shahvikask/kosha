# GrowUrDhan implementation status

Updated: 2026-10-16 after Paparazzi screenshots of the three concept screens. M00 is not accepted.

## Repository

| Item | Value |
|---|---|
| Remote | `https://github.com/shahvikask/kosha` |
| Working branch | `GUD-Android-Redevelopment` |
| Frozen reference | `GUD-Android-Gmail` @ `af96dcc8967ab595e1d9acee78e29e500cb99a9e` |
| GitHub tip at the start of this workflow | `e2fd33ad4f27cd4dd33b13d2d07002247261064b` |
| Workflow-file fix | `99064638e0f1c9ac000ec5ed3379ffbe59041dee` |
| Last accepted SHA | none |
| Native app | scaffold only, application id `in.growurdhan.redevelopment` |
| CI | Kosha CI run 38912221715 passed on the fix SHA. It does not build `android-native/`. The AAB workflow no longer fails at file load on this branch |

## Milestone

**Current:** M00 — not accepted.

| Milestone piece | Status |
|---|---|
| Instruction files and legacy inventory | on GitHub |
| Screen, command, schema v9, and parser-hash inventory | present. Holding forms, holding-detail rows, and home rows are listed. This is not a native screen |
| `android-native/` scaffold | debug APK assembled locally before this repair. Locked, sample home, and sample holding concept screens |
| Lock behavior | `ConceptLock.vaultOpen` is always false. There is no unlock method and no password field |
| Section 6A screenshots | Paparazzi images exist for locked light, sample home dark, and sample holding light. `:app:verifyPaparazziDebug` passed locally. The owner has not reviewed them |
| Dependency review | written in `docs/implementation/DEPENDENCY-REVIEW.md`. Pins were not changed |
| SQLCipher, Chaquopy, financial ledger | module boundaries only. `:core:database` opens nothing and has no tables. `:core:parser-api` does not link Chaquopy and does not call the frozen parser. M24 still owns the schema |
| M24–M34 | NOT STARTED |

## Requirements

P01–P39 and E01–E18 remain **NOT STARTED**. See `parity-register.csv`.

## UI status, separate from financial status

| Surface | Status |
|---|---|
| Locked, sample home, sample holding | Compose source compiled on an earlier SHA. Not rebuilt in this repair |
| Other legacy surfaces | mapped, not built |
| Brand mark | SVG snippets checked against `MarkGeometry` on an earlier SHA. Rendered pixels were not captured |

## Gates

| Gate | Status |
|---|---|
| Local debug assemble | PASS. `:app:assembleDebug` was UP-TO-DATE and finished successfully with `ANDROID_HOME=/opt/android-sdk`. The existing debug APK is 9,511,985 bytes |
| Model JVM tests | PASS. Fresh XML at 2026-10-16T14:24:41Z. 10 tests, 0 failures, in the same Gradle invocation as the app tests |
| Compose semantics | PASS. `:app:testDebugUnitTest` wrote 3 tests, 0 failures, at 2026-10-16T14:24:43Z. The three assertions were not changed. Bitmap capture is still not part of this test |
| Bitmap capture | PASS locally via Paparazzi layoutlib, not Robolectric. See decision D12. Owner review is still pending |
| `./gradlew :core:model:test` | PASS, 10 tests. See decisions D07 and D08 |
| Boundary tests | PASS. `:core:database:test` 1 test at 2026-10-16T14:29:41Z. `:core:parser-api:test` 1 test at 2026-10-16T14:29:43Z. Both 0 failures |
| Native CI | PASS on `63b5b8d5b8cc2352d144c6b8494b4d4bbc3f4340`. Run 38925309401. The log says BUILD SUCCESSFUL. It does not print individual test counts. Screenshot pixels were not produced |
| Emulator and device | NOT RUN |
| Gmail and Play Billing | BLOCKED |
| Security signoff and owner visual acceptance | PENDING |

## Next authorized work

Do not rework the lock flow. Do not start M24. Do not add financial tables. Do not link SQLCipher or Chaquopy yet. Do not dispatch the legacy Tauri AAB. Do not dispatch the native JVM workflow unless the owner says to run it. The owner still needs to review the three concept screenshots.
