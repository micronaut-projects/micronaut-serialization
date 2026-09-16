plugins {
    id("io.micronaut.build.internal.serde-python-examples")
}

dependencies {
    implementation(projects.micronautSerdeJsonb)
    implementation(libs.managed.jakarta.json.bindApi)
    implementation(mn.micronaut.http.client)
}
