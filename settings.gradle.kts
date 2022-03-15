pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "5.2.3"
}

enableFeaturePreview("VERSION_CATALOGS")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "serde-parent"

include("serde-bom")
include("serde-processor")
include("serde-api")
include("serde-jackson")
include("serde-jsonp")
include("serde-support")
include("serde-bson")
include("serde-tck")

include("doc-examples:example-java")
include("doc-examples:example-bson-java")
include("doc-examples:example-jsonb-java")
include("doc-examples:example-kotlin")
include("doc-examples:example-groovy")

val micronautVersion = providers.gradleProperty("micronautVersion")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
//        maven(url="https://s01.oss.sonatype.org/content/repositories/snapshots/")
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
