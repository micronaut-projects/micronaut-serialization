plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
}
dependencies {
    implementation(projects.micronautSerdeJackson)
}
