# AGENTS.md — GrowUrDhan native redevelopment

**Re-read this file before every prompt, code change, test, troubleshooting session, and GitHub Actions decision.** Chat memory is not a source of requirements.

Authoritative requirements live in [docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md](docs/GROK-FULL-NATIVE-REDEVELOPMENT-SPEC.md) (GrowUrDhan Grok Specification **v1.2**). If this file and the specification conflict, the specification wins. Rules **G01–G15**, requirements **P01–P39** and **E01–E18**, **Section 6A**, and **Section 17** are binding.

## Repository boundaries

| Role | Location |
|---|---|
| Original product | `https://github.com/shahvikask/kosha` |
| Frozen functional reference | branch `GUD-Android-Gmail` at commit `af96dcc8967ab595e1d9acee78e29e500cb99a9e` |
| This work | branch `GUD-Android-Redevelopment` only |
| Forbidden | commits on `GUD-Android-Gmail`, `GUD-Android`, `GUD-Android-APK`, `main`, or `vikaskunjbiharishah-ui/GUD` |

Do not merge the separate native repository `vikaskunjbiharishah-ui/GUD` into this experiment.

## Binding rules (read the specification for the full text)

- **G01** Build independently from the frozen legacy source. Never commit on the reference branch.
- **G02** Treat original `src/`, `src-tauri/`, tests, fixtures, documents, and branded assets as read-only parity evidence. New native source goes under `android-native/`. Preserve `src/casparser-main/**` and the protected parser scripts **byte-for-byte**. Do not edit parser algorithms to fix a parse bug.
- **G03** P01–P39 and E01–E18 are compulsory. Map every original screen, field, table, command, and error state. A mismatch needs an owner-approved deviation in `docs/implementation/DECISION-LOG.md`.
- **G04** Production UI is Kotlin + Jetpack Compose + Material 3. No WebView financial UI and no new Tauri/Capacitor shell. Python stays only as the frozen CASPARSER via Chaquopy. No cloud book of household finances.
- **G05** Scaled-integer money (scale 10,000) and quantity (scale 100,000,000). Append-only ledger. Do not invent costs, prices, tax law, ownership, or purchases.
- **G06** Evidence cycle: audit, checklist, implement the whole scope, local tests, diff review, integration tests, coherent commit, then a deliberately dispatched workflow on the exact SHA. Watch that run to a terminal result.
- **G07** Do not fake green. Do not delete or loosen failing tests. An unrun, skipped, cancelled, or older-SHA gate is not a pass.
- **G08** Local checks before costly emulator CI. Avoid push-triggered expensive Android emulator jobs. **Owner standing authorization (2026-10-17):** the owner made `shahvikask/kosha` public and explicitly authorized relevant GitHub Actions development builds and tests without a fresh per-run approval. Deliberately dispatch source-SHA-pinned, milestone-relevant workflows when useful; prefer targeted JVM/build checks before expensive emulator runs, group tests, monitor each run to a terminal result, and record evidence. Public visibility does not itself guarantee every runner or workflow is free. This authorization does **not** permit release publishing, changing protected/frozen branches, exposing secrets, or waiving security/device/owner acceptance.
- **G09** No secrets, PAN, tokens, PDF bytes, vault keys, or real customer statements in Git, logs, CI artifacts, or prompts.
- **G10** Gmail OAuth, Play Billing, signing, and real-device proof stay **BLOCKED** until the owner supplies the service or device. Do not claim those integrations work from unit tests.
- **G11** When CI is authorized, inspect the real run, logs, and artifacts. A queued or running workflow is `RUNNING—NOT VERIFIED`. A new commit needs a new run.
- **G12** Label the stage: `SOURCE MAPPED`, `IMPLEMENTED`, `LOCAL TESTED`, `CI JVM PASSED`, `CI EMULATOR PASSED`, `REAL DEVICE PASSED`, `SECURITY ACCEPTED`, `OWNER ACCEPTED`.
- **G13** Rehydrate from this file, the specification, `docs/implementation/STATUS.md`, `SESSION-HANDOFF.md`, and `DECISION-LOG.md` at the start of every session.
- **G14** Section 6A “Heritage Modern Wealth” is a release gate. Keep the approved GrowUrDhan logo and navy/gold/teal identity. A compiling screen is not a finished screen.
- **G15** Start with the Section 17.3 receipt. End with the Section 17.4 delivery report, an updated `SESSION-HANDOFF.md`, and a next prompt that says to re-read `AGENTS.md`.

## Startup and finish

Before editing:

1. Read this file, the specification sections that apply, `docs/implementation/STATUS.md`, `docs/implementation/SESSION-HANDOFF.md`, `docs/implementation/CI-RUNS.md`, and `docs/implementation/DECISION-LOG.md`.
2. Verify `git remote -v`, branch, `git rev-parse HEAD`, and `git status --short`.
3. Print the Section 17.3 STARTUP COMPLIANCE RECEIPT from observed evidence. Use `UNVERIFIED` or `TO CREATE IN M00` rather than inventing a read.

Before ending a substantial session, update the handoff files and give the Section 17.4 report. Do not mark a milestone accepted without the required tests.

## Response speed and CI efficiency — owner feedback 2026-10-17

**Goal:** avoid the prior 25–30-minute response cycle while preserving G01–G15, the full specification, exact-SHA test evidence, security and milestone acceptance. Prefer a short, substantive first answer and visible verified progress over silently accumulating tool calls. These are execution-efficiency rules, **not permission to skip required reads, testing, signoff, or CI completion**.

1. **Scope the request first.** A process question or short status request needs a focused answer, not a new milestone implementation, emulator run or full repository-documentation rewrite. For a substantial authorized development task, publish the Section 17.3 receipt promptly after verification, then implement the bounded scope.
2. **Parallelize independent read-only operations.** Re-read mandatory AGENTS/spec/tracking once per session; batch independent GitHub file/status reads, retain checked content and exact commit/blob SHAs in session, and re-fetch only when HEAD changes or content is needed again. Do not make repeated huge outputs or serial GitHub calls without a dependency. Source instructions remain authoritative.
3. **Minimize write amplification.** Make a coherent audited source patch, run available cheap/local checks, and commit related files together when supported. Avoid one commit per documentation file, repetitive document-only checkpoint updates or unnecessary push-triggered CI. Consolidate status, decision and handoff updates at the end of the coherent step. Preserve protected-source boundaries and history.
4. **Use the cheapest meaningful CI first.** Prefer targeted JVM/Android Kotlin tests to the emulator; when emulator coverage is necessary, run a grouped milestone workflow **once for the exact source SHA** and inspect its XML/artifacts. Retry only after diagnosing a specific failure or a justified transient infrastructure fault. Never trigger CI just to speed up an answer, and never change tests to manufacture green.
5. **Monitor without polling loops.** Inspect run status at sensible intervals, not every few seconds. Provide concise, substantive progress updates during long work, including the current verified step, run URL/SHA and any blocker. Do not provide invented completion estimates, report a running job as passed or imply background work will finish after the response. Continue to the terminal result when practical under G06/G11; otherwise explicitly report RUNNING—NOT VERIFIED and the limitation.
6. **Make reports compact and nonrepetitive.** Preserve every required Section 17.3 label and Section 17.4 A–U field, but use concise tables, links to authoritative evidence, a specific blocker list and one next prompt. Do not reproduce prior history unless it materially changes the decision. State clearly the last *tested source SHA* versus later documentation HEAD.
7. **Do not sacrifice quality for speed.** No reduced financial-security invariants, historical parity, frozen parser/branding, owner/device acceptance or release restrictions. If the scope is too broad for one response, deliver completed bounded work with honest gaps rather than cycling through unproductive repeats.

## Current stage

M00 is owner-accepted with limitations. M24 remains STARTED / NOT ACCEPTED. M24 current source **JVM 24/24 and Android application Kotlin compilation PASSED** on exact source SHA `38d349a2676a4c99cc9d80dfcf07a1a03efa793d`, targeted run [#39023384556](https://github.com/shahvikask/kosha/actions/runs/39023384556), attempt 2. That JVM/compile run is not device runtime acceptance. **Android API 35 x86_64 emulator instrumentation 7/7 PASSED** at exact SHA `c5ead91ebc332c9a35c3e8e8803dd266039db84a` ([run #39024627068](https://github.com/shahvikask/kosha/actions/runs/39024627068)); a strengthened **7/7 PASSED** at exact SHA `900fa54bf7e73f5e17612dda01a08ed95abd64ec` ([run #39025190962](https://github.com/shahvikask/kosha/actions/runs/39025190962)), including real SQLCipher typed-row commit/reopen, fail-closed duplicate and foreign-key rollback. Emulator != physical device, and full backup-to-new-vault restore/Keystore promotion/owner acceptance remain OPEN. Actual frozen TypeScript exporter/JCE interoperability passed locally with synthetic data, but replay-safe atomic financial restore, Android build/device tests and owner acceptance are still open. **GitHub Actions standing authorization granted 2026-10-17:** relevant targeted M24 and subsequent development builds/tests on the redevelopment branch may be deliberately dispatched without asking again for each run. Preserve cost discipline and exact-SHA verification; M24 remains NOT ACCEPTED until all mandatory gates pass.

**Latest M24 exact-SHA proof 2026-10-17:** targeted [workflow #39048444830](https://github.com/shahvikask/kosha/actions/runs/39048444830) **SUCCESS** on source `dce3b579794d2029a6e3e710978c3dd360c79649`: 24/24 database JVM tests plus 10/10 API35 x86_64 Android instrumentation, including orphaned claim/candidate key/cipher/WAL fail-closed checks and real synthetic encrypted backup/new-vault restoration. Crash-artifact guard is wired to both fresh creation and approved restore. This does **not** establish a recovery/release procedure for permanent claims, historic field-lossless migration, low-storage/crash/power-loss, physical device, AppSec or owner acceptance. M24 **NOT ACCEPTED**; later docs-only heads are not re-tested SHAs.

**Latest M24 frozen legacy encrypted KOSHA1 verification, 2026-10-17:** immutable original `src/lib/backup.ts` allowed absent `kdf` and missing/zero `iterations` in older `KOSHA1` outer envelope, fixed native `LegacyBackupEnvelopeReader` and all three staging decode paths to match strict 600000 PBKDF2-SHA256 default without weakening GCM. Combined [M24 #39056976727](https://github.com/shahvikask/kosha/actions/runs/39056976727) **SUCCESS** at source `327b24211ab6626e17836f941be485f354689636`: JVM **25/25**, Android API35 emulator **14/14**, zero failures/errors/skips, separate actual XML artifacts #12109976229/#12110257579. Initial run #39056752917 failed new test-only Base64 decoder typo and was corrected before green. This does **NOT** make v1 inner financial payloads, v1–v8 historical migration, crash/replay recovery, physical device, AppSec or owner acceptance pass. M24 **NOT ACCEPTED**. Post-source `[skip ci]` docs HEAD is NOT the tested source SHA.

## Loop check

End every response with one line, exactly `Loop: no` or `Loop: yes — <reason>`. A loop is repeating a blocked M00 step, inventorying another screen, or making a documentation-only commit that does not change product behavior. Stopping with no commit is not a loop. Do not continue a loop.
