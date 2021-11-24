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
    implementation("org.glassfish:jakarta.json:2.0.1")
    compileOnly(mn.graal)
    compileOnly(mn.micronaut.jackson.databind)    
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.serdeProcessor)

    testImplementation(mn.jackson.annotations)
    testImplementation("jakarta.json.bind:jakarta.json.bind-api:2.0.0")
    testImplementation(projects.serdeProcessor)
    testImplementation(projects.serdeTck)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mn.micronaut.test.junit5)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testRuntimeOnly(
        "org.junit.jupiter:junit-jupiter-engine"
    )
    testCompileOnly(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.test.spock)
    testImplementation(mn.micronaut.reactor)
}

tasks {
    test {
        useJUnitPlatform()
    }
}