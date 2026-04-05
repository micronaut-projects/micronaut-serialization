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

    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.jackson.dataformat.xml)
    testImplementation(mn.micronaut.sourcegen.annotations)
}
