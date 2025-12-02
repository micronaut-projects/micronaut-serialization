plugins {
    id("com.google.devtools.ksp")
    id("io.micronaut.build.internal.serde-examples")
    id("io.micronaut.build.internal.kotlin-ksp")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
}

dependencies {
    ksp(projects.micronautSerdeProcessor)

    implementation(projects.micronautSerdeJackson)
    implementation(mn.micronaut.http.client)

    runtimeOnly(mnLogging.logback.classic)

    testImplementation(projects.micronautSerdeSupport)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.junit.jupiter.params)
    testImplementation(libs.junit.platform.launcher)
}
