plugins {
    id("groovy")
    id("java-library")
}
dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.serdeProcessor)
    implementation(mn.micronaut.inject.java)
    implementation(projects.serdeApi)
    implementation(projects.serdeSupport)
    implementation(projects.serdeProcessor)
    implementation(mn.micronaut.inject.java.test)
    compileOnly(mn.micronaut.inject.groovy)
    implementation(mnTest.micronaut.test.spock)
    api(libs.jetbrains.annotations)
}
