# CI runs

GitHub Actions were not dispatched by hand. Pushes to `GUD-Android-Redevelopment` start the workflows whose `on.push` filters match.

The AAB workflow file was invalid. GitHub created a failed run with zero jobs on every push, including commits that are not on `GUD-Android`. Run `38909206998` is that failure on `7ac5e95`. The file was corrected in `9906463`. That commit did not start an AAB run, which is what the `GUD-Android` branch filter is supposed to do. The AAB job itself was not executed.

Kosha CI on `9906463` completed successfully. It runs the legacy Node lint, build, and tests. It does not build `android-native/`.

| When (UTC) | Workflow | Run ID | URL | Branch | Tested source SHA | Conclusion | Failed job / test | Artifact | Fix commit |
|---|---|---|---|---|---|---|---|---|---|
| 2026-10-16T13:36:55Z | android-aab.yml | 38909206998 | https://github.com/shahvikask/kosha/actions/runs/38909206998 | GUD-Android-Redevelopment | 7ac5e953cffdddf0bb415fa38a0b9178854fcbe3 | FAIL | workflow file invalid; zero jobs. `secrets` was used in `if` | none | 99064638e0f1c9ac000ec5ed3379ffbe59041dee |
| 2026-10-16T14:00:01Z | Kosha CI | 38912221715 | https://github.com/shahvikask/kosha/actions/runs/38912221715 | GUD-Android-Redevelopment | 99064638e0f1c9ac000ec5ed3379ffbe59041dee | PASS | — | none | — |
