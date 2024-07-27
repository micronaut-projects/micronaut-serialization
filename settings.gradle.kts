pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "7.2.0"
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
include("serde-jackson-tck")
include("serde-tck-tests")
include("serde-oracle-jdbc-json")

include("doc-examples:example-bson-java")
include("doc-examples:example-groovy")
include("doc-examples:example-java")
include("doc-examples:example-jsonb-java")
include("doc-examples:example-kotlin")
include("doc-examples:example-kotlin-ksp")

include("benchmarks")

include("test-suite-tck-jackson-databind")
include("test-suite-tck-serde")
include("test-suite-http-server-tck-netty")

val micronautVersion = providers.gradleProperty("micronautVersion")

configure<io.micronaut.build.MicronautBuildSettingsExtension> {
    useStandardizedProjectNames.set(true)
    importMicronautCatalog()
    importMicronautCatalog("micronaut-reactor")
    importMicronautCatalog("micronaut-oracle-cloud")
    importMicronautCatalog("micronaut-validation")
}
