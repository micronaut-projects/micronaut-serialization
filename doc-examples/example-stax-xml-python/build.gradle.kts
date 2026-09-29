plugins {
    id("io.micronaut.build.internal.serde-python-examples")
}

dependencies {
    implementation(projects.micronautSerdeStaxXml)
    implementation(mn.jackson.dataformat.xml)
    implementation(mn.micronaut.http.client)
}
