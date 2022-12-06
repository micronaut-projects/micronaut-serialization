plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.serdeProcessor)

    api(mn.micronaut.jackson.core)
    api(mn.jackson.annotations)
    api(mn.micronaut.context)
    api(projects.serdeApi)
    implementation(projects.serdeSupport)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.serdeProcessor)
    testImplementation(projects.serdeProcessor)
    testImplementation(projects.serdeTck)
    testImplementation(mn.micronaut.inject.java.test)
    testCompileOnly(mn.micronaut.inject.groovy)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.jackson.databind)
    testImplementation(mn.micronaut.management)
    testImplementation(libs.microstream.storage.restclient)
    testImplementation(libs.aws.lambda.serialization)
    testImplementation(libs.aws.lambda.events)
    testImplementation(libs.micronaut.discovery)
}
