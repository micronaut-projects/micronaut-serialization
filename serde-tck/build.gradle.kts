plugins {
    id("groovy")
    id("java-library")
    id("io.micronaut.build.internal.serde-tests")
}
dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)
    implementation(projects.micronautSerdeApi)
    implementation(projects.micronautSerdeSupport)
    implementation(projects.micronautSerdeProcessor)
    implementation(mn.micronaut.inject.java.test)
    compileOnly(mn.micronaut.inject.groovy)
    implementation(mnTest.micronaut.test.spock)
    api(libs.jetbrains.annotations)
}

tasks.named("spotlessGroovyCheck").configure {
    enabled = false
}
tasks.named("checkstyleMain").configure {
    enabled = false
}
