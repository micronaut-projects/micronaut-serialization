plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
    id("groovy")
}

dependencies {
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testImplementation(platform(mn.boms.jackson))
    testImplementation(mn.micronaut.jackson.databind)
    testImplementation(projects.micronautSerdePropertiesTck)
    testImplementation("tools.jackson.dataformat:jackson-dataformat-properties")
}
