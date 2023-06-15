plugins {
    id("java-library")
    id("io.micronaut.build.internal.serde-tests")
}
dependencies {
    testImplementation(libs.aws.lambda.serialization)
    testImplementation(libs.aws.lambda.events)
    testAnnotationProcessor(projects.micronautSerdeProcessor)
    testImplementation(projects.micronautSerdeJackson)

    testAnnotationProcessor(platform(mn.micronaut.core.bom))
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(platform(mn.micronaut.core.bom))
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)

}
