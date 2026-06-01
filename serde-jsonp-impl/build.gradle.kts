plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    api(libs.managed.jakarta.json.api)

    implementation(mn.micronaut.jackson.core)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.junit.jupiter.api)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

tasks {
    test {
        useJUnitPlatform()
    }

    register("assertNoParssonDependency") {
        group = "verification"
        description = "Verifies that the Micronaut JSON-P provider does not resolve Eclipse Parsson."
        doLast {
            val resolved = configurations.runtimeClasspath.get()
                .resolvedConfiguration
                .resolvedArtifacts
                .map { "${it.moduleVersion.id.group}:${it.name}" }
            check(resolved.none { it == "org.eclipse.parsson:parsson" }) {
                "serde-jsonp-impl must not resolve org.eclipse.parsson:parsson"
            }
        }
    }
}
