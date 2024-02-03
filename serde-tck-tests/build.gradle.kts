plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    annotationProcessor(projects.micronautSerdeProcessor)

    api(mnTest.micronaut.test.junit5)
    api(mn.micronaut.json.core)
    api(mn.jackson.annotations)
    api(projects.micronautSerdeApi)
}

micronautBuild {
    binaryCompatibility {
        enabled.set(false)
    }
}

tasks.named("checkstyleMain").configure {
    enabled = false
}
