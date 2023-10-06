plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
}

dependencies {
    testImplementation(mn.micronaut.jackson.databind)
}
