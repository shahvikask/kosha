# CI runs

GitHub Actions were not dispatched by hand. Pushes to `GUD-Android-Redevelopment` start the workflows whose `on.push` filters match.

The AAB workflow file was invalid. GitHub created a failed run with zero jobs on every push, including commits that are not on `GUD-Android`. Run `38909206998` is that failure on `7ac5e95`. The file was corrected in `9906463`. That commit did not start an AAB run, which is what the `GUD-Android` branch filter is supposed to do. The AAB job itself was not executed.

Kosha CI on `9906463` completed successfully. It runs the legacy Node lint, build, and tests. It does not build `android-native/`.

| When (UTC) | Workflow | Run ID | URL | Branch | Tested source SHA | Conclusion | Failed job / test | Artifact | Fix commit |
|---|---|---|---|---|---|---|---|---|---|
| 2026-10-16T13:36:55Z | android-aab.yml | 38909206998 | https://github.com/shahvikask/kosha/actions/runs/38909206998 | GUD-Android-Redevelopment | 7ac5e953cffdddf0bb415fa38a0b9178854fcbe3 | FAIL | workflow file invalid; zero jobs. `secrets` was used in `if` | none | 99064638e0f1c9ac000ec5ed3379ffbe59041dee |
| 2026-10-16T14:00:01Z | Kosha CI | 38912221715 | https://github.com/shahvikask/kosha/actions/runs/38912221715 | GUD-Android-Redevelopment | 99064638e0f1c9ac000ec5ed3379ffbe59041dee | PASS | — | none | — |
| 2026-10-16T14:01:32Z | Kosha CI | 38912435403 | https://github.com/shahvikask/kosha/actions/runs/38912435403 | GUD-Android-Redevelopment | f29bf13f0bf2e06ca0ceec36573972729b963ee4 | PASS | legacy Node `validate` only | none | — |
| 2026-10-16T14:06:54Z | Kosha CI | 38913154434 | https://github.com/shahvikask/kosha/actions/runs/38913154434 | GUD-Android-Redevelopment | ab73f7d88fe7360778eabafaf712ed5e7d40b556 | PASS | legacy Node `validate` only | none | — |
| 2026-10-16T14:10:17Z | Kosha CI | 38913601615 | https://github.com/shahvikask/kosha/actions/runs/38913601615 | GUD-Android-Redevelopment | 936cf0e929ef1a1c862aca28d6ce506e9cd65e6c | PASS | legacy Node `validate` only | none | — |
| 2026-10-16T14:13:48Z | Kosha CI | 38914074542 | https://github.com/shahvikask/kosha/actions/runs/38914074542 | GUD-Android-Redevelopment | 0720a815bfbb5ce6e924717bf96e36349bf7609c | PASS | legacy Node `validate` only | none | — |
