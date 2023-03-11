plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    implementation(projects.micronautSerdeApi)
    api(mn.micronaut.core.processor)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mn.micronaut.inject.java.test)
    testCompileOnly(mn.micronaut.inject.groovy)
    testImplementation(mnTest.micronaut.test.spock)
}
