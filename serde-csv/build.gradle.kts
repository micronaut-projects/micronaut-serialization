plugins {
    id("io.micronaut.build.internal.serde-module")
}

micronautBuild {
    binaryCompatibility.enabledAfter("3.1.0")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    compileOnly(mn.micronaut.inject.java)

    api(mn.micronaut.context)
    api(projects.micronautSerdeApi)

    implementation(projects.micronautSerdeSupport)
    implementation(projects.micronautSerdeJsonp)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testCompileOnly(mn.micronaut.inject.groovy)

    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(projects.micronautSerdeTck)
    testImplementation(projects.micronautSerdePropertiesTck)
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
