
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    id("io.micronaut.build.internal.serde-examples")
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

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
