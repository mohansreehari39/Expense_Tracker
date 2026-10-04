pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Kharcha-Android"

// Shares the model/domain modules built in Implementation/Core with the
// Windows app, via a composite build — so budget/split/balance math has
// exactly one implementation. See Implementation/Core/README.md.
includeBuild("../Core")

include(":app")
