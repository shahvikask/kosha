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
| 2026-10-16T14:21:57Z | Kosha CI | 38915166321 | https://github.com/shahvikask/kosha/actions/runs/38915166321 | GUD-Android-Redevelopment | 9d54a593d0cd6e2d0fd3a6ff47e0944da935b431 | PASS | legacy Node `validate` only | none | — |
| 2026-10-16T14:25:41Z | Kosha CI | 38915675401 | https://github.com/shahvikask/kosha/actions/runs/38915675401 | GUD-Android-Redevelopment | 1b675d35d9c8c8f679c014d6f17e638f2dfbd9f9 | PASS | legacy Node `validate` only | none | — |
| 2026-10-16T15:35:49Z | GrowUrDhan native JVM | 38925227755 | https://github.com/shahvikask/kosha/actions/runs/38925227755 | GUD-Android-Redevelopment | 2f3c6286402de09180835ded459ec6576c4dcb13 | FAIL | `setup-android@v3` installs removed package `tools` | none | 63b5b8d5b8cc2352d144c6b8494b4d4bbc3f4340 |
| 2026-10-16T15:36:26Z | GrowUrDhan native JVM | 38925309401 | https://github.com/shahvikask/kosha/actions/runs/38925309401 | GUD-Android-Redevelopment | 63b5b8d5b8cc2352d144c6b8494b4d4bbc3f4340 | PASS | — | none | — |
| 2026-10-16T15:46:47Z | Kosha CI | 38926687528 | https://github.com/shahvikask/kosha/actions/runs/38926687528 | GUD-Android-Redevelopment | d7868b215f8a69b10c9170cf7614b74012c279ce | PASS | legacy Node `validate` only | none | — |
