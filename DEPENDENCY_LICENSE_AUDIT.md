# Dependency inventory status

**Status:** Gradle wrapper reviewed; dependency resolution and license inventory not yet generated.

## What is present

- The repository includes a Gradle wrapper, and the README documents JDK 17 and the wrapper-based Android build.
- Dependabot is configured to check Gradle dependencies weekly.
- No Gradle dependency lockfile, Gradle version catalog, or Gradle dependency verification metadata was found in the repository tree reviewed for this audit.

## Required before release

1. Consider enabling Gradle dependency locking for the configurations used by the Android app and committing the resulting lockfiles.
2. Consider Gradle dependency verification metadata so resolved artifacts can be checked against recorded checksums/signatures.
3. Generate a resolved dependency and license inventory for the Android build, including AndroidX, Media3, Compose, Kotlin, and build plugins.
4. Review bundled fonts, icons, artwork, and other assets separately from Gradle dependencies.
5. Re-run the Android build from a clean checkout after any lock or verification metadata is introduced.

## Limitations

This file does not claim that the current dependency graph is fully reproducible or that all third-party license obligations have been satisfied. Dependabot update checks are not a substitute for dependency locking, verification, or a license audit.
