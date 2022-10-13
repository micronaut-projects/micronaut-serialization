plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    implementation(projects.serdeApi)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mn.micronaut.inject.java.test)
    testCompileOnly(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.test.spock)
}
