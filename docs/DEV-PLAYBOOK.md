# GrowUrDhan Native — DEV PLAYBOOK (single working document)

Version 1.0 · written 2026-10-28 by Claude for the owner (Vikas) · target agent: ChatGPT
Repo `shahvikask/kosha`, branch `GUD-Android-Redevelopment` ONLY. Install as `docs/DEV-PLAYBOOK.md`.

**HOW TO USE: read THIS file and nothing else at session start.** Part A = rules (static; edit only on owner request). Part B = live state (the only part you update). Everything else is on-demand lookup.

## Owner approvals (only the owner may change these boxes; the coding agent NEVER ticks or unticks them)
Clauses marked `[O#]` apply only while the box is ticked. All five were ticked on 2026-10-28 by Claude (the drafting assistant) because the owner, Vikas, explicitly delegated the choice in chat ("you can tick as per my requirement understanding and ChatGPT capabilities"). The owner may untick any box at any time; an unticked clause reverts to AGENTS.md/spec behavior.
- [x] O1 Replace "re-read AGENTS/spec/STATUS/HANDOFF every prompt" (G13) and the long §17.3/§17.4 reports (G15) with A1 and A9 of this file.
- [x] O2 Move (not delete) old working docs to `docs/archive/` (A8). Add ONE DECISION-LOG row D58: "process change → DEV-PLAYBOOK v1.0; status: OWNER-DELEGATED (owner asked Claude to choose; owner has not individually reviewed each clause)". Do not word it as a personal owner approval.
- [x] O3 Path-scoped evidence reuse (A6): a green result on an older SHA stays valid when no dependency path changed. Narrow relaxation of G07's older-SHA rule; reuse must always be labeled "reused", never presented as a new run.
- [x] O4 Replace the 58 per-slice workflows with one parametrized `native-ci.yml` (A5). Prove it once on a real SHA BEFORE moving old workflows to `.github/workflows-archive/`. Leave `ci.yml`, `marketing.yml`, `android-aab.yml`, `android-apk.yml` untouched (release/web).
- [x] O5 Committing `.ci/request.json` triggers `native-ci.yml`: its `sha` names a previously committed ancestor on `GUD-Android-Redevelopment` (usually the request commit's parent), not the request commit itself. Require a request-only push, ancestor/branch verification, no other active native-ci run, and no prior green identical (sha, tier, tests); checkout and test requested `sha`. Docs-only commits must never contain a request.

---------------------------------------------------------------------
# PART A — RULES (static)

## A0 Authority
1. Requirements = Spec v1.3 `docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md` (P01–P39, E01–E18, §6A). Spec wins over this file on requirements; this file wins on PROCESS.
2. Never read the spec in full (153 KB). Use A10 line ranges: `sed -n 'A,Bp'`.
3. Owner decision D57 stands: NO financial backup/export/restore/SAF/archive UI; `android:allowBackup="false"`; show permanent-data-loss warning. P03, P28, P29, E05, E12 stay DEFERRED (not "passed").
4. Observations are not owner approvals. Only the owner approves deviations.

## A1 Startup (≤5 tool calls, no narration)
1. Read this file. 2. `git fetch; git rev-parse HEAD; git status --short`. 3. If HEAD ≠ B1.checkpoint_sha: `git diff --stat B1.checkpoint_sha..HEAD` (names only; do not read diffs unless a file is in your slice). 4. List active/recent runs (latest ~10): state + SHA. 5. Print a ≤6-line receipt: branch, HEAD, checkpoint, active runs, top queue item, blockers. Then start working.
Never "audit" items B2 already marks SP/AC unless the queue item says to.

## A2 Hard rules (digest of G01–G15; wording in AGENTS.md if ever in doubt)
- G01/G02 Never commit to other branches. Frozen/read-only: `src/**`, `src-tauri/**`, `src/casparser-main/**` + protected parser scripts (byte-for-byte; never edit parser algorithms), original tests/fixtures/brand assets, SQLCipher v9 schema + frozen upgrade assets, production trust roots. New code only under `android-native/` (+ playbook/docs/workflows/CI).
- G04 Kotlin + Compose + Material 3 only; no WebView finance UI; Python only as frozen CASPARSER (Chaquopy); no cloud book of finances.
- G05 Money scale 10,000, quantity scale 100,000,000 (scaled integers/BigInteger). Append-only ledger. Never invent costs, prices, tax law, ownership, purchases. Unknown cost stays UNKNOWN.
- G07/G11 No fake green. Never delete/loosen/skip failing tests. Queued/running/cancelled = NOT VERIFIED. A result counts only for the SHA it ran on (except A6 once O3 is ticked).
- G09 No secrets/PAN/tokens/PDF bytes/keys/real statements in git, logs, artifacts, prompts.
- G10 Gmail OAuth, Play Billing, signing, real device = BLOCKED until owner supplies them. Unit tests do not prove them.
- G12 Stage labels (use exactly): SOURCE → LOCAL → JVM → EMULATOR → DEVICE → SECURITY → OWNER.
- G14 §6A "Heritage Modern Wealth" is a release gate; compiling ≠ finished. Keep approved logo and navy/gold/teal.
- Owner standing authorization (G08, 2026-10-17): you may run source-SHA-pinned CI for the milestone. Never: publish releases, touch protected branches, expose secrets, waive security/device/owner acceptance.

## A3 Token and time discipline (the main cost drivers)
- Read minimum: `grep -n` for symbols, `sed -n` ranges, `git diff --stat`. Do not open any file >150 lines whole unless editing most of it. Do not re-read a file you just wrote.
- Logs: never load full logs. Extract only failing test names + first 60 lines of each failure + counts from JUnit XML/ADB. Keep run IDs, not log text.
- Never paste file contents, diffs or logs back to the owner. Replies ≤25 lines (A9 template).
- Part B is edited with targeted replacements, not rewritten. Keep this whole file ≤300 lines; if it grows, compress B6/B3 (drop entries older than the last milestone checkpoint).
- Do not create new docs, new per-slice workflows, or per-slice status sections. History lives in git commit messages and Actions runs.
- Extend existing test classes/fixtures; do not duplicate setup code in new classes.
- No exploratory re-audits of the legacy `src/`: open a legacy file only for the exact field/behavior in the slice and only if its B2 row is not already source-mapped.

## A4 Work-slice protocol (one slice = ≥3 related requirements when possible)
1. Pick the top unblocked B4 item. Read its parity needs: spec section by A10 range + only the Kotlin files it touches.
2. Write a ≤8-line Definition of Done (DoD) in the first commit message. DoD must name: screens/fields, SQLCipher create→reopen→negative cases, test classes, G14 viewports (360dp, 200% font, light+dark) when UI.
3. Implement the WHOLE slice (production code + tests) in one pass. No partial "scaffold now, tests later".
4. Local review (no Gradle available to you unless you have it): re-read your diff once for compile errors, imports, nullability, scaling (G05), trust boundaries, and that no assertion was weakened. Fix before pushing.
5. One commit per coherent slice (message = DoD + what changed). Do not push mid-slice commits that trigger CI.
6. Run CI ONCE per A5 tier for that commit. Inspect the terminal result. Repair → new commit → new run (max 2 repair rounds per failure, then stop and report to the owner with the A5 failure table).
7. Update Part B (B1–B4), commit "checkpoint: <slice>", then continue with the next queue item without waiting for the owner unless blocked.
Batch rule: group slices so one emulator run covers several test classes (e.g. P12+P13+P14 together). Never run an emulator job to validate docs, workflows-only or comment changes.

## A5 CI policy
Tiers (pick the lowest that proves the slice):
- T0 no CI: docs, playbook, comments. Never dispatch CI for these.
- T1 JVM tests + `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` (minutes, cheap). Required before any T2.
- T2 targeted API35 instrumentation: only the test classes the slice touched (`-Pandroid.testInstrumentationRunnerArguments.class=<A,B,C>`).
- T3 full regression + G14 matrix + APK/AAB hashes: only at milestone gates (end of M27, M26 acceptance, release candidate) on a clean exact SHA.
Hard rules for every run: test the requested source SHA; workflow_dispatch requires requested SHA = running commit SHA, while a request-file push requires requested SHA to be an existing ancestor on this branch and the push commit to touch only `.ci/request.json`. Reject active native-ci peers and identical (SHA, tier, tests) green runs. Never claim request-commit SHA as tested SHA.

[O4] One workflow `native-ci.yml` (`workflow_dispatch`, optional `.ci/request.json` trigger under [O5]) with inputs `sha`, `tier` (T1|T2|T3), `tests` (comma list of classes), `api` (default 35). Jobs:
1. `preflight` (≈1 min): SHA/branch match; frozen-file hash gate against the existing freeze list; PRINT discovered test counts from source (e.g. grep `@Test`) and pass them forward — NEVER hard-code expected counts in YAML (stale hard-coded counts failed runs 40658684890 and similar).
2. `jvm`: compile + unit tests; upload JUnit XML.
3. `android` (only T2/T3): emulator run of `tests`; upload ADB output, JUnit XML, app/test APK SHA-256.
4. `summary`: one JSON `{sha, tier, tests, jvm_pass, android_pass, apk_sha256, run_id}`; fail if any mandatory job is skipped/failed.
Speed: enable Gradle build cache + `--configuration-cache`; `-x lint` for test tiers; cache the AVD snapshot; `concurrency: group: native-ci-${{ github.ref }}, cancel-in-progress: true`; `paths-ignore: docs/**, marketing/**, **/*.md`.
Wheels (CASPARSER Chaquopy): `native-ci.yml` verifies cached SHA256SUMS or builds four pinned upstream Android CPython 3.13 wheels locally via `.github/scripts/build-native-wheels.sh` and saves them in actions/cache keyed by build script + Android build dependencies. No cross-run artifact downloads/IDs; missing wheels fail closed.
Failure classes (act on the class, not the symptom):
| Class | Signal | Action |
|---|---|---|
| infra | artifact expired, DNS, runner | fix infra/pin; no source change |
| preflight | count/SHA/hash mismatch | fix preflight logic (derive, don't hard-code) |
| compile | Kotlin error | fix source; T1 only |
| test | assertion/instrumentation fail | read the one failing stack; fix source or test expectation ONLY with justification; never loosen |
If the agent cannot dispatch workflows: commit `.ci/request.json` [O5] containing the preceding source commit SHA, tier and tests (one file changed); monitor the run and archive old workflows only after successful native-ci proof.

## A6 Evidence reuse [O3]
Each B3 ledger row stores `tested_sha`, `dep_paths`, `run_id`. A result is reusable at HEAD iff `git diff --name-only <tested_sha> HEAD -- <dep_paths>` is empty. Default `dep_paths` for Android tests: `android-native/core/**`, `android-native/app/src/main/**`, the test file itself, `android-native/gradle/**`, `android-native/app/build.gradle.kts`. State "reused from <run_id> at <sha>" explicitly; never call it a new run. If O3 is not ticked, only same-SHA results count.

## A7 Chat-limit protocol (the reason this file exists)
- The repo is your memory. After every slice and before the chat gets long, make the checkpoint (A4.7). A new chat then needs only: this file.
- Never rely on chat history for requirements, SHAs or run results; Part B holds them.
- If you notice context running low: finish the current commit, update Part B, then stop with the A9 report. Do not start a new slice.

## A8 Old docs [O2]
Move (git mv, do not delete — they are evidence) to `docs/archive/`: `docs/implementation/STATUS.md`, `SESSION-HANDOFF.md`, `CI-RUNS.md`, `parity-register.csv`, `*-ACCEPTANCE.md`, `HOLDING-*.md`, `HOME-ROWS.md`. Keep in place: spec, `DECISION-LOG.md` (append-only, short rows), `BASE-RELEASE-SCOPE.md`, `AGENTS.md` (add one line at top: "Process: follow docs/DEV-PLAYBOOK.md"). Read archived files only via `grep` for a specific run id/SHA/term, never whole.

## A9 Session report (≤25 lines, replaces §17.4 once [O1] is ticked)
```
SLICE: <ids> · STAGE: <SOURCE|LOCAL|JVM|EMULATOR|...>
COMMITS: <short shas>   RUNS: <id → pass/fail/running @sha>
VERIFIED: <what is proven, counts>   NOT VERIFIED: <what is not>
FAILED/REPAIRED: <class + one line>
NEXT: <top queue item>   BLOCKED ON OWNER: <list or none>
```
New-chat prompt the owner pastes (replaces §17.5/17.7):
`Read docs/DEV-PLAYBOOK.md only. Follow A1, then continue the top item in B4. Update Part B at each checkpoint. Do not re-read other docs.`

## A10 Spec lookup (line ranges in v1.3 file; run `grep -n '^#' ` if shifted)
§2 architecture 101–179 · §3 screens 180–201 · §4.1 categories 204–220 · §4.2 HoldingDetails 221–228 · §4.3 events 229–238 · §5 P-register 239–288 · §6 E-register 289–317 · §6A design 318–555 (6A.4 tokens 376, 6A.6 Home 419, 6A.7 screens 445, 6A.10 G14 matrix 520) · §7 migration 556–588 · §8 milestones 589–611 · §10 CI 649–751 · §12 tests 804–844 · §13 integrations 845–860 · §14 DoD 901–913.

---------------------------------------------------------------------
# PART B — LIVE STATE (edit only this part)
Seed written by Claude on 2026-10-28 from repo docs. FIRST SESSION: verify B1–B3 against GitHub Actions and fix any mismatch before relying on them.

## B1 Header
branch GUD-Android-Redevelopment · checkpoint_sha `aeaca643b6ae53adbacd53698192c56fb8105da4` (E09 T2 request commit) · E09 T1 `41079924267` at source `28a29a90` PASS; focused API35 `41080764097` same source PASS 5/5 with original APK SHA256/ADB and same-run summary; E15/E17/E18 last T1 `41013903115` at `62c2f11c` PASS partial Maven OSV; accepted requirements 0/52 · no formal milestones.
State codes: ND not started · SP source partial · JV JVM green · EM emulator green · AC accepted · DF deferred (D57).

## B2 Requirement matrix (split a row when members diverge)
| IDs | Milestone | State | Notes / next gap |
|---|---|---|---|
| P01–P02 | M25 | SP/EM | Enrollment admission recheck before vault creation; acceptance pending |
| P03, P28, P29, E05, E12 | — | DF | D57; acceptance pending |
| P04–P05 | M24 | SP/EM | SQLCipher schema + append-only ledger tests green at older SHAs; acceptance pending |
| P06–P08, E03, E04 | M26 | SP/EM | Cross-owner custody, goal, HUF/minor/guardian and primary protections encrypted rollback→restar; acceptance pending |
| P09–P10 | M27 | SP/EM | Eight-category encrypted restart/negative and 36-key specialist mapping T2 40682410679 API35 9/; acceptance pending |
| P11 | M24/M27 | SP/EM | specialist metadata, PPF/SSY non-posting, property proof tests green at 3746d928; acceptance pending |
| P12 | M27 | SP/EM | Owner-scoped detail links, edit, goal, archive/reopen and dated-price negative restart checks; acceptance pending |
| P13–P14 | M27 | SP/EM | Source-referenced snapshot read-only history/reconciliation preview and correction-picker restr; acceptance pending |
| P15 | M27 | SP/EM | Owner-scoped filtered/sorted bounded lazy holdings with stable IDs and history, account links; acceptance pending |
| P16, P21, P24 | M29 | SP/EM | Owner-scoped encrypted hybrid review + honest Home subset, exact 10000bp; acceptance pending |
| P22–P23, P25 | M29 | SP/EM | Interest coupon and EMI due-date/one-post goldens; acceptance pending |
| P17–P20, E06 | M28 | SP/EM | Non-demat CAS preview exact ISIN/folio identity, duplicate rejection and multi-folio negatives ; acceptance pending |
| P26–P27, E10 | M30 | SP/EM | Frozen-aligned NSE ZIP/BSE CSV allowlisted dated-close adapters, manual owner-only refresh and ; acceptance pending |
| P30 | M31 | SP/EM | In-memory masked dossier now wired into real Family owner lens with separate warning acknowledg; acceptance pending |
| P31 | M31 | SP/EM | Authenticated theme, SGB market/RBI-required and owner-entered unverified IBJA reference API35 ; acceptance pending |
| P32, E11 | M31 | SP/EM | Encrypted opt-in/revoke and no diagnostic logs, redacted manual text preview/copy/clear; acceptance pending |
| P33, E01, E02, E08, E13 | M31 | SP/EM | Hindi/device-loss and 360dp/200%-font light/dark onboarding. Real-screen owner-safe Settings→Fa; acceptance pending |
| P34 | M32 | SP | idempotency/cursor guard only; acceptance pending |
| P35, E07 | M32 | SP/EM | API35 40936450262 66/66 PASS at 5063de3b; fake gateway/SQLCipher mapping only, OAuth blocked |
| P36 | M31/M33 | SP/EM | Feedback API35 40962454191 5/5; Privacy SQLCipher/Compose/API35 40967478400 6/6 at 4eeb4323, original ADB/APK reviewed; final owner/legal Privacy approval pending |
| P37, E16 | M33 | SP/EM | Play Billing v8 read-only catalog UI and 48dp Settings checks 41004099432 API35 4/4 at 40837e84; T1 41003704910 PASS; verifier leases/JVM goldens. Genuine Play purchases/verification/trial/enforcement owner-blocked |
| P38–P39 | M33–M34 | ND | Release signoff/pipeline work pending |
| E09 | M34 | SP/EM | Exact 10000bp category concentration, income/realized years, dated goal progress; explicit self-reported contribution/expense calendar series (not bank-verified) with owner-scoped SQLCipher INSERT-only v9 settings, negative/restart + 360dp 200% light/dark G14. T1 41079924267 PASS, T2 41080764097 5/5 API35 at source 28a29a90, original APK SHA256/ADB inspected. Device/security/owner acceptance open |
| E15 | M34 | SP/JV | Partial debug-runtime CycloneDX graph, offline secret/egress tests and bounded public Maven OSV scan (NO_OSV_ADVISORIES_REPORTED) T1 41013903115 at 62c2f11c. Not full release SBOM or MASVS/independent pen-test; owner/security gates open |
| E17 | M34 | SP/EM | Exact-SHA source guard, same-run original APK bytes/hash verification and quality-evidence SHA binding in CI summary; T2 41012633253 (source 9d678f4e) PASS 5/5 and T1 41013903115 (source 62c2f11c) PASS. Summary releaseReady=false; production signer, full regression, DEVICE/OWNER open |
| E18 | M34 | SP/JV | Fixed-source HTTPS/Play egress inventory, RAM-only cold-init and fixed-name public price/Play error metrics, negative Python tests and T1 41013903115 at 62c2f11c PASS; broader runtime egress/privacy assessment and physical-device validation open |
| E14 | all | SP | modular tests partial; acceptance pending |

## B3 Latest passing runs (SHA = tested source, not request-commit HEAD; older rows archived)
| run_id | SHA | group |
|---|---|---|
| 41080764097 | 28a29a90 | E09 manual cash + G14 5/5 API35 PASS; app/test APK SHA256, ADB, same-run summary; request aeaca643 |
| 41079924267 | 28a29a90 | E09 exact manual cash JVM + Android/test compile T1 PASS; request 49730d6f |
| 41013903115 | 62c2f11c | E15/E17/E18 T1 PASS; partial OSV NO_OSV_ADVISORIES_REPORTED; request-commit 5c3b2f07 |
| 41012633253 | 9d678f4e | E09 encrypted goal-progress + G14 5/5 API35 PASS; same-run original app/test APK hashes checked; request-commit df6d9eb8 |
| 41012011536 | 9d678f4e | E09/E17 source T1 PASS; request-commit 890e4a5e |
| 41007990761 | 2e4fbba9 | E09/E18 real-screen API35 4/4 PASS |
| 41007491341 | 2e4fbba9 | E09/E15/E17/E18 JVM + offline quality evidence PASS |
| 41004099432 | 40837e84 | P37/E16 read-only Billing Settings API35 4/4 PASS |
| 41003704910 | 40837e84 | P37/E16 source T1 PASS |
| 40974691187 | 0f8d4c56 | P37/E16 Billing SDK source T1 PASS |
| 40936450262 | 5063de3b | M29 + P35/E07 API35 66/66 PASS |
| 40848824289 | e0b6a74e | M31 settings PASS |
| 40845095146 | 78b772da | M31 privacy PASS |
| 40811450454 | 2d006e52 | M31 dossier PASS |
| 40809097105 | a1fedcd6 | M30 prices PASS |
| 40759156459 | e21966a7 | M28 CAS PASS |
| 40751028900 | 5a9a5f4e | M29 P25 PASS |
| 40737480182 | 6f5cb3c5 | M25 enrollment PASS |
| 40705791566 | b38de8be | M26 ownership PASS |
| 40696534614 | 2445eb76 | G14 PASS |
| 40690449183 | c3f534b4 | M27 P15 PASS |
| 40687321312 | 19ad3288 | M27 P12–14 PASS |
| 40682410679 | 3f1a808f | M27 P09–10 PASS |
| 40967478400 | 4eeb4323 | P36 Privacy SQLCipher/Compose API35 6/6 PASS |
| 40962454191 | 888caf54 | P36 feedback SQLCipher API35 5/5 PASS |
| 40966792647 | 4eeb4323 | P36 Privacy SQLCipher/Compose T1 PASS |
| 40499402394 | 511aa0ea | M27 reconciliation PASS |

## B4 Work queue (top unblocked first)
1. DONE unified native CI and workflow archive.
2. P09/P10 EM: eight categories and 36-key mapping; owner/device open.
3. P12 EM: detail/edit/archive/prices; owner/G14 open.
4. P13/P14 EM: snapshot/correction guard; reconciliation open.
5. P15 EM: lazy holdings/filter/sort; G14 open.
6. G14 EM: screenshots; owner/device open.
7. M26 EM: owner/custody/goal; device/security open.
8. M25 EM: enrollment/Terms; device/security open.
9. M29 P24/P25: original 40926146593 65/66 fixed without weakening guards; 40936450262 API35 66/66 PASS at 5063de3b, original APK hashes/ADB reviewed.
10. M28 EM: synthetic CAS; real CAS/ARM64 blocked.
11. M30 EM: priced-feed UI; actual network/device blocked.
12. M31 EM: dossier/settings/privacy; owner/device blocked.
13. E09 manual cash SOURCE→JVM→EMULATOR complete for automated slice: T1 41079924267 PASS and T2 41080764097 5/5 PASS at tested source 28a29a90; original same-run APK SHA256/ADB checked. Failed fixture T1 41079329580 preserved (literal backslash vs control char) and corrected, no test loosened. E09 remains SP/EM pending genuine device/owner acceptance; annotations self-reported and not bank receipts. NEXT: E15/E17/E18 fail-closed source egress and security evidence hardening; external full release SBOM, MASVS pen test, production signer, physical DEVICE, full T3 and OWNER approval remain blocked. No release acceptance.
14. BLOCKED-OWNER: Gmail OAuth, genuine Play purchase verification, final Privacy Policy, approved signing fingerprint/keys, actual ARM64 device and CAS evidence, independent AppSec/pen test, G14 owner sign-off.
Process P0 completed: unified native-ci T1/T2; 54 old workflows archived.

## B5 Blockers needing the owner
Gmail OAuth project/credentials; Play Billing products/test accounts; upload/signing keys; physical ARM64 phone + genuine CAS statements; production property/PPF/SSY/valuer trust roots; independent AppSec reviewer; G14 screenshot approval. (O1–O5 are already ticked via owner delegation; workflow triggering uses `.ci/request.json`.)

## B6 Do-not-redo list
- Do not re-audit rows marked SP in B2; do not re-run green jobs for unchanged dependency paths (A6 once O3 ticked).
- Do not re-derive D57 scope, frozen hashes, or branding.
- Do not rewrite passing tests; do not add per-slice workflows or docs.
- Known past failures to avoid: hard-coded test counts in workflow YAML; pinning an expired artifact id; duplicate dispatch while a run is active; treating lightweight/docs CI as Android proof.

P35/E07: SOURCE `82ec5b76`; 5 new Android tests not run. Active M29 T2 `40919538632` remains NOT VERIFIED. No parallel native CI.

## B7 Open risks
Evidence path/SHA scoped: newest E09 T2 41080764097 tested source 28a29a90 (not request HEAD aeaca643) and T1 41079924267 same source; no Android main/test changes after source, reusable under A6 if dependency path diff empty. Partial OSV no-advisory report does NOT certify full dependencies or AppSec. Owner inputs block full M32–M34; 0 formal acceptance. Next E15/E17/E18 source-only hardening, then external pen test, signer, DEVICE/OWNER.