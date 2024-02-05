plugins {
    java
}
repositories {
    mavenCentral()
}

dependencies {
    implementation(mn.micronaut.http.server.tck) {
        exclude("io.micronaut", "micronaut-jackson-databind")
    }
    implementation(projects.micronautSerdeJackson)

    runtimeOnly(mnLogging.logback.classic)

    testAnnotationProcessor(platform(mn.micronaut.core.bom))
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(mnValidation.micronaut.validation.processor)

    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(libs.junit.platform.engine)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(mnValidation.micronaut.validation)
}
tasks.withType<Test> {
    useJUnitPlatform()
}
