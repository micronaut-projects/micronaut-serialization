plugins {
    id("io.micronaut.build.internal.serde-examples")
    id("io.micronaut.build.internal.kotlin-kapt")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
}

dependencies {
    kapt(projects.micronautSerdeProcessor)

    implementation(projects.micronautSerdeJackson)
    implementation(mn.micronaut.http.client)

    runtimeOnly(mnLogging.logback.classic)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.junit.platform.launcher)
}
