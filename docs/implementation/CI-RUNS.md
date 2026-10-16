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
| 2026-10-16T15:36:26Z | Kosha CI | 38925309172 | https://github.com/shahvikask/kosha/actions/runs/38925309172 | GUD-Android-Redevelopment | 63b5b8d5b8cc2352d144c6b8494b4d4bbc3f4340 | PASS | legacy Node `validate` only | none | — |
| 2026-10-16T15:40:32Z | Kosha CI | 38925857816 | https://github.com/shahvikask/kosha/actions/runs/38925857816 | GUD-Android-Redevelopment | 6fa90f526c3589dc1953f5d6050105782e4cbcff | PASS | legacy Node `validate` only | none | — |
| 2026-10-16T15:46:47Z | Kosha CI | 38926687528 | https://github.com/shahvikask/kosha/actions/runs/38926687528 | GUD-Android-Redevelopment | d7868b215f8a69b10c9170cf7614b74012c279ce | PASS | legacy Node `validate` only | none | — |
| 2026-10-16T15:50:18Z | Kosha CI | 38927152231 | https://github.com/shahvikask/kosha/actions/runs/38927152231 | GUD-Android-Redevelopment | e551472498760f46ab0f3a5b248e963b496f2323 | PASS | legacy Node `validate` only | none | — |
| 2026-10-16T15:53:05Z | Kosha CI | 38927524450 | https://github.com/shahvikask/kosha/actions/runs/38927524450 | GUD-Android-Redevelopment | d348eff4dd2fddab75fae6b6ba4dc2122f7362ff | PASS | legacy Node `validate` only | none | — |
| 2026-10-16T15:58:26Z | Kosha CI | 38928223155 | https://github.com/shahvikask/kosha/actions/runs/38928223155 | GUD-Android-Redevelopment | dfb18a84bc64f099004e421f955cfb8415a8d128 | PASS | legacy Node `validate` only | none | — |
| 2026-10-16T18:39:00Z | GrowUrDhan M00 Real Emulator Screenshots | 38948959678 | https://github.com/shahvikask/kosha/actions/runs/38948959678 | GUD-Android-Redevelopment | `430c171782ff967e4f107fb46f7c863ade35faf3` | PASS | Job `M00 API35 Compose real-pixel capture` succeeded. Terminal conclusion observed 2026-10-16T18:46:46Z. The first write of this row was RUNNING—NOT VERIFIED. | `m00-api35-real-compose-screenshots`, 433184 bytes, not expired. Three of six PNGs opened this session. Not a new owner acceptance. | none |
| 2026-10-16T18:39:00Z | Kosha CI | 38948959536 | https://github.com/shahvikask/kosha/actions/runs/38948959536 | GUD-Android-Redevelopment | `430c171782ff967e4f107fb46f7c863ade35faf3` | PASS | legacy Node `validate` only. Terminal conclusion replaces the RUNNING checkpoint first written on this row. | none | none |
| 2026-10-16T18:41:32Z | Kosha CI | 38949274452 | https://github.com/shahvikask/kosha/actions/runs/38949274452 | GUD-Android-Redevelopment | accd2bf8ca277a1d6f72c48f83d643a2f2507897 | PASS | legacy Node `validate` only | none | — |
