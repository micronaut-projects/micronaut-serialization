import jdk.internal.vm.vector.VectorSupport.test

plugins {
    id("io.micronaut.build.internal.serde-module")
}

micronautBuild {
    // No previously published version to compare against yet
    binaryCompatibility {
        enabled.set(false)
    }
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    api(projects.micronautSerdeCsv)
    api(mn.micronaut.jackson.core)
    api(mn.micronaut.context)

    implementation(projects.micronautSerdeSupport)
    implementation(libs.managed.jackson.dataformat.csv)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testCompileOnly(mn.micronaut.inject.groovy)

    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(projects.micronautSerdeTck)
    testImplementation(projects.micronautSerdeCsvTck)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks {
    test {
        useJUnitPlatform()
    }
}
