plugins {
    id("io.micronaut.build.internal.serde-module")
}

configurations.all {
    exclude("io.micronaut", "micronaut-jackson-databind")
    exclude("io.micronaut", "micronaut-jackson-core")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.serdeProcessor)

    api("jakarta.json:jakarta.json-api:2.0.1")
    api(mn.micronaut.context)
    api(projects.serdeApi)
    implementation(projects.serdeSupport)
    compileOnly(mn.graal)
    compileOnly(mn.micronaut.jackson.databind)
    testImplementation("org.glassfish:jakarta.json:2.0.1")
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