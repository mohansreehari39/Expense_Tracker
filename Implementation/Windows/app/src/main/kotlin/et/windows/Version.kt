package et.windows

/**
 * Keep in sync with `build.gradle.kts`'s `compose.desktop.application.
 * nativeDistributions.packageVersion` by hand — a Compose Desktop (non-
 * Android) target has no AGP-style `BuildConfig` generation to read this
 * from automatically. Bump both together on every release; see
 * `et.windows.update.UpdateChecker`, which compares this against the
 * latest published GitHub release tag.
 */
const val APP_VERSION = "0.1.0"
