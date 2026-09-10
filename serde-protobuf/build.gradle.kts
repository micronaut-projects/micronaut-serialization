plugins {
    id("io.micronaut.build.internal.serde-module")
}

micronautBuild {
    // no previously published version to compare against yet
    binaryCompatibility {
        enabled.set(false)
    }
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    api(mn.micronaut.context)
    api(projects.micronautSerdeApi)

    implementation(projects.micronautSerdeSupport)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.protobuf.java)

    testImplementation(mnTest.junit.jupiter.params)

    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

tasks {
    test {
        useJUnitPlatform()
    }
}
