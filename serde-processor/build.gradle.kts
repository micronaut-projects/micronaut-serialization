plugins {
    id("io.micronaut.build.internal.module")
}

dependencies {
    implementation(mn.jackson.annotations)
    implementation(project(":serde-api"))

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mn.micronaut.inject.java.test)
    testCompileOnly(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.test.spock)
}