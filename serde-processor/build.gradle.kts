plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    compileOnly(mn.micronaut.core.processor)

    implementation(projects.micronautSerdeApi)
    implementation(libs.micronaut.sourcegen.model)
    implementation(libs.micronaut.sourcegen.generator)
    implementation(libs.micronaut.sourcegen.generator.java)

    testAnnotationProcessor(mn.micronaut.inject.java)

    testCompileOnly(mn.micronaut.inject.groovy)
    // Loaded by the Groovy compilation of the tests, which runs the processor visitors
    testCompileOnly(libs.micronaut.sourcegen.annotations)

    testImplementation(mn.micronaut.inject.groovy.test)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.spock)
}
