# Reproducible builds

Heartwood aims for **bit-for-bit reproducible** release APKs so that F-Droid (and anyone
else) can independently verify that a published binary was built from this source.

## What's already done in this repo

| Concern | How it's pinned |
| --- | --- |
| Gradle | `gradle/wrapper/gradle-wrapper.properties` → **8.13** (always use `./gradlew`) |
| Android Gradle Plugin | `gradle/libs.versions.toml` → **8.13.2** |
| Kotlin | `gradle/libs.versions.toml` → **2.2.21** |
| All libraries | Exact versions in `gradle/libs.versions.toml`, no `+` / dynamic versions / ranges |
| Transitive deps | Locked in `app/gradle.lockfile` (`dependencyLocking { lockAllConfigurations() }`) |
| Play dependency metadata | Stripped via `dependenciesInfo { includeInApk = false }`, this block is signed by Google and is **not** reproducible; removing it is required for F-Droid repro builds |
| JDK | **17** (Temurin), see CI |
| SDK | `compileSdk`/`targetSdk` **36**, `build-tools;36.0.0`, no NDK |

R8/AGP already write deterministic ZIP timestamps, so a clean checkout built with the
toolchain below produces a stable unsigned APK; signing is layered on afterward and does
not change the signed-content comparison F-Droid performs.

## Toolchain contract (for the Nix flake)

A reproducing environment must provide exactly:

- `jdk17` (Temurin/Adoptium 17)
- Android SDK with: `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`
- `ANDROID_HOME` / `ANDROID_SDK_ROOT` pointing at that SDK
- Gradle is **not** needed on PATH, the wrapper bootstraps 8.13

Build command:

```sh
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk` (unsigned unless signing env/props
are present, see `app/build.gradle.kts`).

## Nix notes

The toolchain pins above are trivial for Nix (`androidenv.composeAndroidPackages`). The one
wrinkle is Gradle's network fetches breaking sandbox purity. Two clean options:

1. **gradle2nix**, generates a fixed-output derivation from the lockfile; the build then
   runs fully offline. Recommended.
2. **Fixed-output derivation around `./gradlew --offline`**, seed a Gradle cache as an FOD
   whose hash is pinned against `app/gradle.lockfile`, then build offline.

`app/gradle.lockfile` is committed precisely so a Nix FOD has a stable input to hash. After
changing dependencies, regenerate it with:

```sh
./gradlew :app:dependencies --write-locks
```
