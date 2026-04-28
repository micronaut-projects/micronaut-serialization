plugins {
    id("java")
    id("io.micronaut.build.internal.serde-examples")
}

dependencies {
    implementation(projects.micronautSerdeJackson)
    compileOnly(projects.micronautSerdeProcessor)
    compileOnly(mn.jackson.databind)
    annotationProcessor(projects.micronautSerdeProcessor)
    annotationProcessor(mn.micronaut.inject.java)

    testImplementation(libs.junit.platform.engine)
    testImplementation(libs.junit.jupiter.engine)
}

tasks {
    test {
        useJUnitPlatform()
    }
}