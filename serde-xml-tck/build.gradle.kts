plugins {
    id("groovy")
    id("java-library")
    id("io.micronaut.build.internal.serde-tests")
}
dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    compileOnly(mn.micronaut.inject.groovy)
    compileOnly(mn.jackson.databind)
    api(libs.jetbrains.annotations)

    implementation(projects.micronautSerdeApi)
    implementation(projects.micronautSerdeSupport)
    implementation(projects.micronautSerdeProcessor)
    implementation(mn.micronaut.inject.java.test)
    implementation(mnTest.micronaut.test.spock)
    compileOnly(mn.jackson.dataformat.xml) {
        exclude(group = "com.fasterxml.woodstox", module = "woodstox-core")
        exclude(group = "org.codehaus.woodstox", module = "stax2-api")
    }
    runtimeOnly(mn.jackson.annotations)
}

tasks.named("spotlessGroovyCheck").configure {
    enabled = false
}
tasks.named("checkstyleMain").configure {
    enabled = false
}
