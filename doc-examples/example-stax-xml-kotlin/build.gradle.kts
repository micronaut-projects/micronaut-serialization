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

    implementation(projects.micronautSerdeStaxXml)
    compileOnly(mn.jackson.dataformat.xml)
    compileOnly(libs.managed.jakarta.xml.bindApi)
    implementation(mn.micronaut.http.client)

    runtimeOnly(mnLogging.logback.classic)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.junit.platform.launcher)
}
