plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    // android() target is added once Implementation/Android starts consuming this module.

    sourceSets {
        commonMain.dependencies {
            implementation(project(":model"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            // javax.crypto (JDK-provided) backs the JVM actual Crypto implementation.
        }
    }
}
