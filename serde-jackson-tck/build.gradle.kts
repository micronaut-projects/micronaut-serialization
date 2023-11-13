plugins {
    id("groovy")
    id("java-library")
    id("io.micronaut.build.internal.serde-tests")
}
dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    api(projects.micronautSerdeApi)
    api(projects.micronautSerdeSupport)
    api(projects.micronautSerdeProcessor)
    api(mn.micronaut.inject.java.test)
    api(mnTest.micronaut.test.spock)
    api(libs.jetbrains.annotations)
    api(mn.jackson.annotations)

    compileOnly(mn.micronaut.inject.groovy)
    compileOnly(mn.jackson.databind)
}

tasks.named("spotlessGroovyCheck").configure {
    enabled = false
}
tasks.named("spotlessJavaCheck").configure {
    enabled = false
}
tasks.named("checkstyleMain").configure {
    enabled = false
}
