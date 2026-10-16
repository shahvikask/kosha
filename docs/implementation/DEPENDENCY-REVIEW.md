# Dependency review — M00 scaffold pins

Reviewed 2026-10-16 from `android-native/gradle/libs.versions.toml`, `android-native/gradle/wrapper/gradle-wrapper.properties`, the local Gradle cache, and the public pages named below. This review does not change the pins. It does not add SQLCipher, Chaquopy, or a ledger.

## Pins that compiled

| Piece | Pin | What the last local build resolved |
|---|---|---|
| Android Gradle Plugin | 8.7.3 | `com.android.tools.build:gradle:8.7.3` |
| Kotlin, including the Compose compiler plugin | 2.0.21 | same version on the compiler plugin |
| Gradle wrapper | 8.11.1 | used for the debug assemble |
| Compose BOM | 2024.12.01 | UI 1.7.6, Material 3 1.3.1, from the BOM POM in the local cache |
| JDK toolchain | 17 | `jvmToolchain(17)` |
| compileSdk / targetSdk | 35 / 35 | `app/build.gradle.kts` |
| minSdk | 26 | matches the specification floor |

`activity-compose` 1.9.3 and `core-ktx` 1.15.0 are pinned beside the BOM. Robolectric 4.14.1 is a test tool. No product database library is on the classpath.

## Compatibility

The Kotlin Gradle compatibility table at [kotlinlang.org/docs/gradle-configure-project.html](https://kotlinlang.org/docs/gradle-configure-project.html), read on 2026-10-16, lists Kotlin 2.0.20–2.0.21 as fully supporting Gradle through 8.8 and AGP through 8.5. A footnote on that page says Gradle 8.7–8.10 is also supported, with a Kotlin Multiplatform `withJava()` warning. This scaffold is not Kotlin Multiplatform.

This project uses AGP 8.7.3 and Gradle 8.11.1. Both are newer than that fully supported row. The same page says newer Gradle and AGP versions may still compile, with possible deprecation warnings. The local debug assemble and the Compose semantics tests already compiled on this combination. That is evidence of a successful compile here, not evidence that the combination is inside Kotlin’s fully supported range.

AGP 8.7 release notes say the highest API level that AGP 8.7 supports is 35. compileSdk 35 matches that limit. [Play’s target API page](https://developer.android.com/google/play/requirements/target-sdk), read on 2026-10-16, says that from 31 August 2026 a new app or an update must target Android 16, API 36. Today is after that date. This scaffold is not a Play submission. AGP 8.7 cannot compile API 36, so a later Play upload needs a newer AGP. That upgrade is not part of this review.

AGP 9.0, described on [developer.android.com/build/migrate-to-built-in-kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin), turns on built-in Kotlin and rejects a second `org.jetbrains.kotlin.android` plugin. Moving there is a migration, not a patch-level bump.

## Age and known gaps

The Android Compose release page, read on 2026-10-16, lists stable Compose UI 1.12.1 and Material 3 1.4.0, updated 7 October 2026. The pinned BOM is 2024.12.01, which selects UI 1.7.6 and Material 3 1.3.1. The scaffold is behind the current stable Compose line. No CVE for those exact coordinates was confirmed in this review. Maven Repository listed no vulnerabilities on AGP 8.7.3 itself. This was not an OWASP scan and not a review of every transitive jar.

The wrapper properties do not set `distributionSha256Sum`. The file published at `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256` on this date was `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`. It was not written into the wrapper, and the zip was not hashed again locally.

There is no Gradle dependency lock and no SBOM. Section 2.1 asks for both before release. They are not created here.

There is no NDK library in this scaffold, so the 16 KiB page-size requirement does not apply to a binary that does not exist. SQLCipher and Chaquopy, when they are added, must be checked for 16 KiB alignment. They are not added in this change.

## Decision

Keep the current pins for the concept screens. Do not jump to Kotlin 2.4, AGP 9, or a 2025/2026 Compose BOM in the same change as this note. A toolchain upgrade needs its own compile, the existing semantics tests, and an owner decision because it can change Compose behavior and the Kotlin plugin setup.
