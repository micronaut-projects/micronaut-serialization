plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
}

dependencies {
    testImplementation(projects.micronautSerdeJaxbTck)
    testImplementation(libs.jaxb.runtime)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}
