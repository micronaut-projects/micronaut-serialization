plugins {
    id("io.micronaut.build.internal.serde-base")
    id("java-library")
}

dependencies {
    testImplementation(platform(mn.micronaut.core.bom))
    testImplementation(projects.micronautSerdeJackson)
    testImplementation(projects.micronautSerdeSupport)
    testImplementation(mn.micronaut.dev.tck)
    // the reload harness compiles the application under test with the processors on the test classpath
    testImplementation(mn.micronaut.inject.java)
    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(platform(mnTest.micronaut.test.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(mnLogging.logback.classic)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}