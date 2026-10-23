plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
    id("groovy")
}

dependencies {
    testImplementation(projects.micronautSerdeToonTck)
    testImplementation(libs.jtoon)
    testImplementation(mn.jackson.databind)
}
