# GrowUrDhan implementation status

Updated: 2026-10-16. The owner accepted milestone M00 against `e551472498760f46ab0f3a5b248e963b496f2323`. The limitations below stay open.

## Repository

| Item | Value |
|---|---|
| Remote | `https://github.com/shahvikask/kosha` |
| Working branch | `GUD-Android-Redevelopment` |
| Frozen reference | `GUD-Android-Gmail` @ `af96dcc8967ab595e1d9acee78e29e500cb99a9e` |
| GitHub tip at the start of this workflow | `e2fd33ad4f27cd4dd33b13d2d07002247261064b` |
| Workflow-file fix | `99064638e0f1c9ac000ec5ed3379ffbe59041dee` |
| Last accepted SHA | `e551472498760f46ab0f3a5b248e963b496f2323` for M00, with limitations |
| Native app | scaffold only, application id `in.growurdhan.redevelopment` |
| CI | Kosha CI run 38912221715 passed on the fix SHA. It does not build `android-native/`. The AAB workflow no longer fails at file load on this branch |

## Milestone

**Current:** M00 — owner-accepted, with limitations. M24 is not started.

| Milestone piece | Status |
|---|---|
| Instruction files and legacy inventory | on GitHub |
| Screen, command, schema v9, and parser-hash inventory | present. Holding forms, holding-detail rows, and home rows are listed. This is not a native screen |
| `android-native/` scaffold | debug APK assembled locally. Locked, sample home, and sample holding concept screens |
| Lock behavior | `ConceptLock.vaultOpen` is always false. There is no unlock method and no password field |
| Section 6A screenshots | Owner accepted the three Paparazzi images on `d7868b215f8a69b10c9170cf7614b74012c279ce` |
| Dependency review | written in `docs/implementation/DEPENDENCY-REVIEW.md`. Pins were not changed |
| SQLCipher, Chaquopy, financial ledger | still boundaries only. Not linked. M24 owns the schema |
| M24–M34 | NOT STARTED |

## Requirements

P01–P39 and E01–E18 remain **NOT STARTED**. See `parity-register.csv`.

## UI status, separate from financial status

| Surface | Status |
|---|---|
| Locked, sample home, sample holding | Compose source compiled on an earlier SHA. Not rebuilt in this repair |
| Other legacy surfaces | mapped, not built |
| Brand mark | Visible in the accepted concept screenshots. This is not a separate logo audit |

## Gates

| Gate | Status |
|---|---|
| Local debug assemble | PASS. `:app:assembleDebug` was UP-TO-DATE and finished successfully with `ANDROID_HOME=/opt/android-sdk`. The existing debug APK is 9,511,985 bytes |
| Model JVM tests | PASS. Fresh XML at 2026-10-16T14:24:41Z. 10 tests, 0 failures, in the same Gradle invocation as the app tests |
| Compose semantics | PASS. `:app:testDebugUnitTest` wrote 3 tests, 0 failures, at 2026-10-16T14:24:43Z. The three assertions were not changed. Bitmap capture is still not part of this test |
| Bitmap capture | PASS locally via Paparazzi. The owner accepted the three images. See decision D13 |
| `./gradlew :core:model:test` | PASS, 10 tests. See decisions D07 and D08 |
| Boundary tests | PASS. `:core:database:test` 1 test at 2026-10-16T14:29:41Z. `:core:parser-api:test` 1 test at 2026-10-16T14:29:43Z. Both 0 failures |
| Native CI | PASS on `63b5b8d5b8cc2352d144c6b8494b4d4bbc3f4340`. Run 38925309401. The log says BUILD SUCCESSFUL. It does not print individual test counts. Screenshot pixels were not produced |
| Emulator and device | NOT RUN |
| Gmail and Play Billing | BLOCKED |
| Security signoff and owner visual acceptance | Concept screenshots accepted. M00 accepted by the owner with limitations. Security signoff is not done |

## Accepted limitations

These were not closed by the owner's acceptance:

- SQLCipher and Chaquopy are not linked, and there is no financial table.
- Native JVM run 38925309401 passed on `63b5b8d`, not on the accepted tip, and it did not run Paparazzi.
- No emulator, device, or security review has passed.
- P01–P39 and E01–E18 are not started.
- Screens other than the three concept screens are mapped, not built.

## Next authorized work

Do not rework the lock flow. Do not start M24 unless the owner asks. Do not add financial tables. Do not link SQLCipher or Chaquopy yet. Do not dispatch the legacy Tauri AAB. Do not dispatch the native JVM workflow unless the owner says to run it.
