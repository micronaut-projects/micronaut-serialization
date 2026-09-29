plugins {
    id("io.micronaut.build.internal.serde-python-examples")
}

dependencies {
    implementation(projects.micronautSerdeProtobuf)
    // only so the example can weigh a protobuf payload against the equivalent JSON
    implementation(projects.micronautSerdeJackson)
}
