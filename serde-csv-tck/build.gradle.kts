plugins {
    id("groovy")
    id("java-library")
    id("io.micronaut.build.internal.serde-tests")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    compileOnly(mn.micronaut.inject.groovy)
    compileOnly(libs.managed.jackson.dataformat.csv)

    api(projects.micronautSerdeCsv)
    api(mn.micronaut.inject.java.test)
    api(mnTest.micronaut.test.spock)
}

tasks.named("spotlessGroovyCheck").configure {
    enabled = false
}
tasks.named("checkstyleMain").configure {
    enabled = false
}
