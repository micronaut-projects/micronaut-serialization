plugins {
    id("io.micronaut.build.internal.serde-module")
}
dependencies {
    api(mnTest.micronaut.test.junit5)
    api(mn.micronaut.json.core)
}
micronautBuild {
    binaryCompatibility {
        enabled.set(false)
    }
}
tasks.named("checkstyleMain").configure {
    enabled = false
}
