plugins {
    id("java")
    id("io.micronaut.build.internal.serde-examples")
}

dependencies {
    implementation(projects.micronautSerdeJackson)
    compileOnly(projects.micronautSerdeProcessor)
    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    annotationProcessor(projects.micronautSerdeProcessor)
    annotationProcessor(libs.micronaut.inject)

    testImplementation(libs.junit.platform.engine)
    testImplementation(libs.junit.jupiter.engine)
}

tasks {
    test {
        useJUnitPlatform()
    }
}