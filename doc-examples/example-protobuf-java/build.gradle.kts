plugins {
    id("java")
    id("io.micronaut.build.internal.serde-examples")
}

micronaut {
    runtime("none")
    testRuntime("junit5")
}

dependencies {
    annotationProcessor(projects.micronautSerdeProcessor)

    implementation(projects.micronautSerdeProtobuf)
    // only so the example can weigh a protobuf payload against the equivalent JSON
    implementation(projects.micronautSerdeJackson)

    runtimeOnly(mnLogging.logback.classic)

    testImplementation(mnTest.micronaut.test.junit5)
}

application {
    mainClass.set("example.Application")
}
