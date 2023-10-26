plugins {
    java
}
repositories {
    mavenCentral()
}

dependencies {
    testAnnotationProcessor(platform(mn.micronaut.core.bom))
    testAnnotationProcessor(mn.micronaut.inject.java)
    implementation(mn.micronaut.http.server.tck) {
        exclude("io.micronaut", "micronaut-jackson-databind")
    }
    implementation(projects.micronautSerdeJackson)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(libs.junit.platform.engine)
    testImplementation(libs.junit.jupiter.engine)
    runtimeOnly(mnLogging.logback.classic)
    testAnnotationProcessor(mnValidation.micronaut.validation.processor)
    testImplementation(mnValidation.micronaut.validation)
}
tasks.withType<Test> {
    useJUnitPlatform()
}