plugins {
    id("java-library")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    api(libs.managed.jakarta.xml.bindApi)
    api(mnTest.junit.jupiter.api)

    implementation(mn.micronaut.inject.java)
    implementation(projects.micronautSerdeApi)
    implementation(libs.xmlunit.core)
}
