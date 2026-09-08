plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
    id("groovy")
}

dependencies {
    testImplementation(projects.micronautSerdeYamlTck)
    testImplementation(libs.jackson.dataformat.yaml)
}
