plugins {
    id("io.micronaut.build.internal.serde-tck-suite")
    id("groovy")
}

dependencies {
    testImplementation(platform("tools.jackson:jackson-bom:3.2.0"))
    testImplementation(projects.micronautSerdeYamlTck)
    testImplementation("tools.jackson.dataformat:jackson-dataformat-yaml")
}
