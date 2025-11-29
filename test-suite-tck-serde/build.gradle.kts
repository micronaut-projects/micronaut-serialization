plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
}

dependencies {
    implementation(projects.micronautSerdeJackson)
    testImplementation(mnTest.junit.platform.suite)
}
