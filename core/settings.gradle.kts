// Lets the rules engine build and test on its own (no Android SDK needed): ./gradlew -p core test
// When the root build includes :core, this file is ignored.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "core"
