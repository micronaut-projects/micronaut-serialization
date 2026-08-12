plugins {
    id("io.micronaut.build.internal.serde-module")
}

micronautBuild {
    // No published artifact to baseline against until this module ships.
    binaryCompatibility.enabledAfter("3.1.1")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    // Streaming CBOR only (CBORFactory/Parser/Generator). Exclude databind — object
    // mapping is Micronaut Serde, not Jackson ObjectMapper/CBORMapper.
    api(libs.managed.jackson.dataformat.cbor) {
        exclude(group = "tools.jackson.core", module = "jackson-databind")
    }
    api(mn.micronaut.context)
    api(projects.micronautSerdeApi)

    // Streaming Encoder/Decoder bridge over tools.jackson.core (not databind).
    implementation(projects.micronautSerdeJackson)
    implementation(projects.micronautSerdeSupport)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testCompileOnly(mn.micronaut.inject.groovy)

    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(projects.micronautSerdeTck)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.jackson.annotations)
}

tasks {
    test {
        useJUnitPlatform()
    }
}
