plugins {
    id("io.micronaut.build.internal.module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.serdeProcessor)

    api("jakarta.json:jakarta.json-api:2.0.1")
    api(mn.micronaut.context)
    api(projects.serdeApi)
    compileOnly(mn.micronaut.jackson.databind)
    testImplementation("org.eclipse:yasson:2.0.2")

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(projects.serdeProcessor)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mn.micronaut.test.junit5)
    testRuntimeOnly(
        "org.junit.jupiter:junit-jupiter-engine"
    )
}

tasks {
    test {
        useJUnitPlatform()
    }
}