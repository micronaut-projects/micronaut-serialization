plugins {
    id("groovy")
    id("java-library")
    id("io.micronaut.build.internal.serde-tests")
}
dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    compileOnly(mn.micronaut.inject.groovy)

    api(libs.jetbrains.annotations)

    implementation(projects.micronautSerdeApi)
    implementation(projects.micronautSerdeSupport)
    implementation(projects.micronautSerdeProcessor)
    implementation(mn.micronaut.inject.java.test)
    implementation(mnTest.micronaut.test.spock)
}

tasks.named("spotlessGroovyCheck").configure {
    enabled = false
}
tasks.named("checkstyleMain").configure {
    enabled = false
}
