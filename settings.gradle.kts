pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "4.2.1"
}

enableFeaturePreview("VERSION_CATALOGS")

rootProject.name = "serde-parent"

include("serde-processor")
include("serde-api")
include("serde-jackson")
include("serde-support")

val micronautVersion = providers.gradleProperty("micronautVersion")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("mn") {
            from("io.micronaut:micronaut-bom:${micronautVersion.get()}")
        }
    }
}