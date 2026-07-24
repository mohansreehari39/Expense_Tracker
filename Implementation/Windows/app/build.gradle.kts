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
    }
}
