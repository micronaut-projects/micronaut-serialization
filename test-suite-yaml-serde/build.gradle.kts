plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
    id("groovy")
}

dependencies {
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    // generates the bean definitions the @MicronautTest Groovy specifications are injected into
    testCompileOnly(mn.micronaut.inject.groovy)

    testImplementation(projects.micronautSerdeYaml)
    testImplementation(projects.micronautSerdeYamlTck)
}
