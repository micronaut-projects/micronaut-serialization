plugins {
    id("io.micronaut.build.internal.serde-python-examples")
}

dependencies {
    implementation(projects.micronautSerdeStaxXml)
    implementation(mn.jackson.dataformat.xml)
    implementation(libs.managed.jakarta.xml.bindApi)
    implementation(mn.micronaut.http.client)
}
