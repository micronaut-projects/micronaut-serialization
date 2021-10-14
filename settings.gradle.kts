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

rootProject.name = "serialization-parent"

include("serialization-base")
include("serialization-generator")
include("serialization-api")
include("json-generated-impl")

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