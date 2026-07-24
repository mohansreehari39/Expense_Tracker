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

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
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
            packageName = "ExpenseTracker"
            packageVersion = "0.1.0"
            description = "Household and trip expense tracker — dashboard and server"
            vendor = "Expense Tracker"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }
        }
    }
}
