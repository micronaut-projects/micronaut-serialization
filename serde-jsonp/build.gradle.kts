plugins {
    id("io.micronaut.build.internal.module")
}

repositories {
    maven { setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
    mavenCentral()
}

configurations.all {
    exclude("io.micronaut", "micronaut-jackson-databind")
    exclude("io.micronaut", "micronaut-jackson-core")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.serdeProcessor)

    api(libs.managed.jakarta.json.api)
    api(mn.micronaut.context)
    api(projects.serdeApi)
    implementation(projects.serdeSupport)
    implementation(libs.managed.glassfish.jakarta.json)
    compileOnly(mn.graal)
    compileOnly(mn.micronaut.jackson.databind)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.serdeProcessor)

    testImplementation(mn.jackson.annotations)
    testImplementation(libs.managed.jakarta.json.bindApi)
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
