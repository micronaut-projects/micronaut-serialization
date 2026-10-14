plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
    id("org.graalvm.buildtools.native")
}

dependencies {
    implementation(projects.micronautSerdeForyJson)
}
