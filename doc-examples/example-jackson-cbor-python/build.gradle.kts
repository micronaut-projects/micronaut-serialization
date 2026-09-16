plugins {
    id("io.micronaut.build.internal.serde-python-examples")
}

dependencies {
    implementation(projects.micronautSerdeJacksonCbor)
    implementation(mn.micronaut.http.server.netty)
    implementation(mn.micronaut.context.python.netty)
    implementation(mn.micronaut.http.client)
}
