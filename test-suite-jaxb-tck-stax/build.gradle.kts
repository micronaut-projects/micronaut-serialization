plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
}

dependencies {
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testImplementation(projects.micronautSerdeJaxbTck)
    testImplementation(projects.micronautSerdeStaxXml)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}
