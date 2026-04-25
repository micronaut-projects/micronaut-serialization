plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    api(mn.micronaut.context)
    api(projects.micronautSerdeApi)

    implementation(projects.micronautSerdeSupport)
    implementation("tools.jackson.dataformat:jackson-dataformat-toml")

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testCompileOnly(mn.micronaut.inject.groovy)

    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.spock)
}

tasks {
    test {
        useJUnitPlatform()
    }
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("5.0.0")
    }
}
