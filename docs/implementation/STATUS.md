# GrowUrDhan implementation status

Updated: 2026-10-16 after the home-screen row inventory. M00 is not accepted.

## Repository

| Item | Value |
|---|---|
| Remote | `https://github.com/shahvikask/kosha` |
| Working branch | `GUD-Android-Redevelopment` |
| Frozen reference | `GUD-Android-Gmail` @ `af96dcc8967ab595e1d9acee78e29e500cb99a9e` |
| GitHub tip at the start of this inventory | `936cf0e929ef1a1c862aca28d6ce506e9cd65e6c` |
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
| Section 6A screenshots | NOT CAPTURED. Compose semantics for the three screens passed locally on an earlier SHA |
| Dependency review | written in `docs/implementation/DEPENDENCY-REVIEW.md`. Pins were not changed |
| SQLCipher, Chaquopy, financial ledger | not started |
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
| Local debug assemble | PASS on an earlier SHA. Not repeated for this workflow-only change |
| Model JVM tests | 10 tests PASS via `org.junit.runner.JUnitCore` on an earlier SHA |
| Compose semantics | `ConceptVisualTest` 3 tests, 0 failures, on an earlier SHA |
| Bitmap capture | FAILED to produce an image. See decision D04 |
| `./gradlew :core:model:test` | NOT PASSED. The Gradle test worker still drops its socket |
| GitHub Actions | Kosha CI run 38913601615 passed on `936cf0e`. Legacy Node tests only. It does not build `android-native/` |
| Emulator and device | NOT RUN |
| Gmail and Play Billing | BLOCKED |
| Security signoff and owner visual acceptance | PENDING |

## Next authorized work

Do not rework the lock flow. Do not upgrade AGP, Kotlin, or Compose unless the owner asks. Do not start M24. Do not add a database. Do not dispatch the legacy Tauri AAB. Do not build the native holding form. Screenshot pixels remain blocked. The holdings list is the next unread screen.
