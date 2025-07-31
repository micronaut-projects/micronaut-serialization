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
    api("org.junit.jupiter:junit-jupiter-params")
    implementation(projects.micronautSerdeJmespath)

    api(mnTest.micronaut.test.junit5)
    api(libs.jetbrains.annotations)
}

tasks.named("spotlessJavaCheck").configure {
    enabled = false
}
tasks.named("checkstyleMain").configure {
    enabled = false
}
