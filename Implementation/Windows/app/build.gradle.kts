import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("WindowsDatabase") {
            // Row classes are generated under Schema.sq's own directory
            // (src/main/sqldelight/et/windows/db/sql/), which must match
            // packageName below. Deliberately distinct from et.core.model /
            // et.windows.db to avoid class-name collisions between generated
            // row types and domain entities sharing the same name (Trip,
            // Household, Category, ...).
            packageName.set("et.windows.db.sql")
        }
    }
}

dependencies {
    implementation("et.core:model")
    implementation("et.core:sync")
    implementation("et.core:domain")

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.sqldelight.driver.sqlite)
    implementation(libs.sqldelight.coroutines)

    implementation(libs.zxing.core)
    implementation(libs.jmdns)

    // Without a real SLF4J backend, Ktor swallows server-side exceptions
    // entirely (they'd otherwise be logged via SLF4J's "Application"
    // logger) — this is what made a 500 from the household budget
    // schema change silent instead of showing a stack trace.
    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

// Runs a second, independent instance for testing alongside a real
// installed/running one — separate port and data directory (see
// KharchaConfig.kt), selected via a JVM system property passed only to
// this task, never an OS environment variable and never present in the
// packaged installer. Usage: `gradlew.bat :app:runDev` from Windows.
tasks.register<JavaExec>("runDev") {
    group = "application"
    description = "Runs Kharcha with a separate dev port/data directory, for testing alongside a real installed instance."
    mainClass.set("et.windows.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("kharcha.dev", "true")
}

compose.desktop {
    application {
        mainClass = "et.windows.MainKt"

        nativeDistributions {
            // Exe = a double-click installer (needs WiX Toolset on the
            // Windows machine that runs `packageExe`); jpackage can't
            // cross-build a Windows package from Linux/WSL, so this must
            // be run on Windows itself. See Implementation/Windows/README.md.
            targetFormats(TargetFormat.Exe)
            packageName = "Kharcha"
            packageVersion = "0.1.0"
            description = "Kharcha — household and trip expense tracker"
            vendor = "Kharcha"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                iconFile.set(project.file("icon.ico"))
                // Stable across versions so a later installer upgrades this
                // install in place (Add/Remove Programs) instead of creating
                // a second entry. Generated once for this project — do not
                // regenerate.
                upgradeUuid = "8f2e6b3a-3f7f-4b8a-9b0a-6c2e2b4f6d31"
            }
        }
    }
}
