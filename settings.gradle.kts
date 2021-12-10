pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("build-logic") {
        name = "serde-build-logic"
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "4.2.6"
}

enableFeaturePreview("VERSION_CATALOGS")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "serde-parent"

include("serde-processor")
include("serde-api")
include("serde-jackson")
include("serde-jsonp")
include("serde-support")
include("serde-bson")
include("serde-tck")
include("serde-aws-events")

// examples
include("doc-examples:example-java")
include("doc-examples:example-bson-java")
include("doc-examples:example-jsonb-java")
include("doc-examples:example-kotlin")
include("doc-examples:example-groovy")

val micronautVersion = providers.gradleProperty("micronautVersion")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("mn") {
            from("io.micronaut:micronaut-bom:${micronautVersion.get()}")
        }
        create("libs") {
            alias("bson").to("org.mongodb:bson:${providers.gradleProperty("bsonVersion").get()}")
        }
    }
}