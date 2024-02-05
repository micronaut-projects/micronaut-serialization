plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    compileOnly(mn.micronaut.core.processor)

    implementation(projects.micronautSerdeApi)

    testAnnotationProcessor(mn.micronaut.inject.java)

    testCompileOnly(mn.micronaut.inject.groovy)

    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.spock)
}
