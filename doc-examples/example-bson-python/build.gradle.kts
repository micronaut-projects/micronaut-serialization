plugins {
    id("io.micronaut.build.internal.serde-python-examples")
}

dependencies {
    implementation(projects.micronautSerdeBson)
    implementation(mn.micronaut.http.client)
}
