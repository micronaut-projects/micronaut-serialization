plugins {
    id("java")
    id("io.micronaut.build.internal.serde-examples")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
}
dependencies {
    annotationProcessor(projects.micronautSerdeProcessor)
    implementation(projects.micronautSerdeJsonp)
    implementation(mn.micronaut.http.client)
    runtimeOnly(mnLogging.logback.classic)
    testImplementation(mnTest.micronaut.test.junit5)

    implementation(libs.managed.jakarta.json.bindApi)
}
application {
    mainClass.set("example.Application")
}
