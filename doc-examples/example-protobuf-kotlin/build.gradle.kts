plugins {
    id("io.micronaut.build.internal.serde-examples")
    id("io.micronaut.build.internal.kotlin-kapt")
}

micronaut {
    runtime("none")
    testRuntime("junit5")
}

dependencies {
    kapt(projects.micronautSerdeProcessor)

    implementation(projects.micronautSerdeProtobuf)
    // only so the example can weigh a protobuf payload against the equivalent JSON
    implementation(projects.micronautSerdeJackson)

    runtimeOnly(mnLogging.logback.classic)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.junit.platform.launcher)
}
