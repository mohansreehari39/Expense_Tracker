pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "expense-tracker-windows"

// Depends on the model/sync/domain modules built in Implementation/Core,
// via a composite build so Core stays independently buildable — see
// Implementation/Core/README.md.
includeBuild("../Core")

include(":app")
