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

    compileOnly(mn.micronaut.inject.java)

    api(mn.micronaut.context)
    api(projects.micronautSerdeApi)

    implementation(projects.micronautSerdeSupport)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testCompileOnly(mn.micronaut.inject.groovy)

    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(projects.micronautSerdeTck)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.spock)

    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

configurations.configureEach {
    exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
}

tasks {
    test {
        useJUnitPlatform()
    }
}
