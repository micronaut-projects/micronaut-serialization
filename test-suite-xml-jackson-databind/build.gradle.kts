plugins {
    id("groovy")
    id("io.micronaut.build.internal.serde-tck-suite")
}

dependencies {
    testImplementation(projects.micronautSerdeXmlTck)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.jackson.dataformat.xml)
}
