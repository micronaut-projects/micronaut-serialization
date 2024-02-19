plugins {
    id("java")
    id("io.micronaut.build.internal.serde-native-examples")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
}

dependencies {
    annotationProcessor(projects.micronautSerdeProcessor)

    implementation(projects.micronautSerdeJsonp)
    implementation(mn.micronaut.http.client)
    implementation(libs.managed.jakarta.json.bindApi)

    runtimeOnly(mnLogging.logback.classic)

    testImplementation(mnTest.micronaut.test.junit5)
}

application {
    mainClass.set("example.Application")
}
