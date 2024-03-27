plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("io.micronaut.build.internal.serde-examples")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
}

dependencies {
    kapt(projects.micronautSerdeProcessor)
    kapt(mnValidation.micronaut.validation.processor)

    implementation(projects.micronautSerdeJackson)
    implementation(mn.micronaut.http.client)
    implementation(mnValidation.micronaut.validation)
    implementation("jakarta.validation:jakarta.validation-api")

    runtimeOnly(mnLogging.logback.classic)

    testImplementation(mnTest.micronaut.test.junit5)
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
