plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
    id("groovy")
}

dependencies {
    testImplementation(platform(mn.boms.jackson))
    testImplementation(projects.micronautSerdeCsvTck)
    testImplementation("tools.jackson.dataformat:jackson-dataformat-csv")
}
