plugins {
    id("io.micronaut.build.internal.serde-module")
}

micronautBuild {
    // No published artifact to baseline against until this module ships in 3.2.0.
    binaryCompatibility.enabledAfter("3.2.0")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    api(mn.micronaut.context)
    api(projects.micronautSerdeApi)

    // Low level event based YAML parser/emitter only; object mapping is Micronaut Serde.
    implementation(libs.managed.snakeyaml.engine)
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
}

tasks {
    test {
        useJUnitPlatform()
    }
}
