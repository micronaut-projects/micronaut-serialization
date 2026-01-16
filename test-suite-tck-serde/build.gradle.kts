plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
    id("groovy")
}

dependencies {
    implementation(projects.micronautSerdeJackson)

    testImplementation(projects.micronautSerdeJacksonTck)
}
