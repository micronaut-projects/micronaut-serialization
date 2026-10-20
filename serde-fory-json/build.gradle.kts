plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    api(mn.micronaut.context)
    api(projects.micronautSerdeApi)
    api(libs.fory.json)

    implementation(projects.micronautSerdeSupport)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testCompileOnly(mn.micronaut.inject.groovy)

    testImplementation(mn.jackson.annotations)
    testImplementation(mn.jackson.databind)
    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(projects.micronautSerdeTck)
    testImplementation(projects.micronautSerdeJacksonTck)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.spock)

    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

tasks {
    test {
        useJUnitPlatform()
    }
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("3.1.2")
    }
}
