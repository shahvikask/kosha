# GrowUrDhan — mandatory freshest session handoff

**UTC:** 2026-10-16. **Remote:** `https://github.com/shahvikask/kosha.git`, **work branch** `GUD-Android-Redevelopment` ONLY. **Starting HEAD:** `3a7254dd41d120c65215efa82920145ea9ffc226`. Verify latest 40-character GitHub HEAD at next session — this file cannot contain its own commit. Frozen untouched reference `GUD-Android-Gmail@af96dcc8967ab595e1d9acee78e29e500cb99a9e`. Working tree UNVERIFIED via GitHub-only edit.

## Read-first evidence and stage

Re-read root AGENTS.md, complete v1.2 spec (G01–G15, P04/P05/M24, Section 17), STATUS, CI-RUNS, DECISION-LOG, parity register, previous M24 checkpoint and frozen TypeScript migration source. **M00 OWNER ACCEPTED** with limitations; **M24 STARTED / NOT ACCEPTED**. No M25+ started.

## Implementation this turn

- `android-native/core/database/src/main/kotlin/in/growurdhan/database/SchemaV9MigrationPlan.kt`: pure read-only source-version phase model, no SQL I/O, no vault opener.
- `android-native/core/database/src/test/kotlin/in/growurdhan/database/TestOnlyV9Migration.kt`: frozen SHA-pinned source migration interpreter in disposable SQLCipher JDBC databases, version consistency/quickcheck/FK/type checks, bounded supported 1..8, atomic DDL+metadata updates, rollback, no destructive reset.
- `android-native/core/database/src/test/kotlin/in/growurdhan/database/TestOnlyV9MigrationTest.kt`: 6 new synthetic JUnit tests **authored but not run**: version plan, 1–8 upgrade fixture matrix, populated encrypted backup/reopen with nested JSON and scaled money/quantity+two synthetic owners, failure injection atomic rollback, corrupt/incompatible metadata/version/structure, corrupt ciphertext and wrong-key rejection.
- The frozen modern SCHEMA_V1 already includes many earlier v3/v4/v7 optional fields. Test fixtures explicitly omit a few removable columns in *synthetic* older projections. Real historic snapshots/backups and all legacy layout variants remain **UNVERIFIED**.
- No code under original `src/`, `src-tauri/`, frozen CASPARSER, approved logo, GitHub Actions or production SQLCipher/Keystore/Chaquopy changed. Legacy CI push is suppressed via commit message `[skip ci]` to respect user CI budget.

## Verification and provenance

- Three staged Kotlin files balanced lexical checks; source history/delta mapping agrees with frozen v1.2 schema. 18 final tables/217 columns/13 indexes/3 triggers still expected.
- **Local kotlinc** compiled the *exact Git blob* `SchemaV9MigrationPlan.kt` (hash matched `77c8689b520a5f8bcc826eeadf169bf357c9046e`) and ran 12 targeted plan smoke assertions PASS.
- **NOT RUN** full Kotlin module Gradle, SQLCipher/JDBC new tests, Android, emulator, hardware, restore or AppSec. New test count: 6 methods. **No GitHub Actions authorized/dispatched**. Last accepted previous M24 JVM [run 38954683658](https://github.com/shahvikask/kosha/actions/runs/38954683658) PASS 7/7 on earlier `ebc8d703a5e6791ae63bfca8383f196ea9bf7ec5`. Current latest M24 migration source **untested by CI**.

## Next bounded task

1. Re-read full instructions, verify newest GitHub HEAD and branch; review committed migrations/test code for compile/semantic bugs.
2. **Only with explicit owner's new approval**, invoke source-pinned `:core:database:test` Gradle/JVM workflow on current exact GUD-Android-Redevelopment SHA. Monitor to terminal and inspect JUnit failures; repair source/test assertions only with factual evidence and rerun corrected SHA with authorization.
3. Source golden captured historical migrations, strict lossless roundtrips, production Android encrypted vault/Keystore, backup failures still outstanding. **Do not mark M24 accepted** until full evidence and owner decision.
4. Return A–U report, actual source SHA and next copy/paste prompt beginning `Re-read AGENTS.md`.

Loop: no
