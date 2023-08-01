pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "6.5.4"
}

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
include("serde-oracle-jdbc-json")

include("doc-examples:example-bson-java")
include("doc-examples:example-groovy")
include("doc-examples:example-java")
include("doc-examples:example-jsonb-java")
include("doc-examples:example-kotlin")

include("benchmarks")

val micronautVersion = providers.gradleProperty("micronautVersion")

configure<io.micronaut.build.MicronautBuildSettingsExtension> {
    useStandardizedProjectNames.set(true)
    importMicronautCatalog()
    importMicronautCatalog("micronaut-reactor")
    importMicronautCatalog("micronaut-logging")
}
