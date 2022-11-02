plugins {
    id("io.micronaut.build.internal.bom")
}

micronautBom {
    suppressions {
        acceptedVersionRegressions.add("glassfish-jakarta-json")
        acceptedLibraryRegressions.add("glassfish-jakarta-json")
    }
}
