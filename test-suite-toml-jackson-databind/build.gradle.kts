plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
    id("groovy")
}

dependencies {
    testAnnotationProcessor(mn.micronaut.inject.java)

    testImplementation(mn.micronaut.jackson.databind)
    testImplementation(projects.micronautSerdeTomlTck)
    testImplementation("tools.jackson.dataformat:jackson-dataformat-toml")
}
