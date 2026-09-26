plugins {
    id("io.micronaut.build.internal.serde-python-examples")
}

dependencies {
    implementation(projects.micronautSerdeJackson)
    implementation(projects.micronautSerdeYaml)
    implementation(projects.micronautSerdeProperties)
    implementation(mn.micronaut.http)
}
