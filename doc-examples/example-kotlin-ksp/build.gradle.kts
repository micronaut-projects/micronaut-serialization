import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    ksp(mnValidation.micronaut.validation.processor)

    implementation(projects.micronautSerdeJackson)
    implementation(mn.micronaut.http.client)
    implementation(mnValidation.micronaut.validation)
    implementation("jakarta.validation:jakarta.validation-api")

    runtimeOnly(mnLogging.logback.classic)

    testImplementation(mnTest.micronaut.test.junit5)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
