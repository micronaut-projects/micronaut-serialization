import org.gradle.kotlin.dsl.mnTest

plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    annotationProcessor(projects.micronautSerdeProcessor)

    api(mn.micronaut.context)
    api(projects.micronautSerdeApi)

    implementation(mn.micronaut.context)
    implementation(mn.micronaut.core)
    implementation(projects.micronautSerdeApi)
    implementation(mn.snakeyaml)
    implementation(projects.micronautSerdeSupport)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testCompileOnly(mn.micronaut.inject.groovy)

    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(projects.micronautSerdeTck)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mn.micronaut.inject.kotlin.test)
    testImplementation(mn.micronaut.inject.kotlin)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mnTest.junit.jupiter.api)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}




