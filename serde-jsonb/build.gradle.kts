plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    api(libs.managed.jakarta.json.bindApi)
    api(libs.managed.jakarta.json.api)
    api(projects.micronautSerdeApi)

    compileOnly(libs.jakarta.enterprise.cdi.api)

    implementation(projects.micronautSerdeJackson)
    implementation(projects.micronautSerdeSupport)
    implementation(projects.micronautSerdeJsonpImpl)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)
    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.junit.jupiter.api)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("3.1.0")
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    register("assertNoYassonDependency") {
        group = "verification"
        description = "Verifies that the Micronaut JSON-B provider does not resolve Eclipse Yasson."
        doLast {
            val resolved = configurations.runtimeClasspath.get()
                .resolvedConfiguration
                .resolvedArtifacts
                .map { "${it.moduleVersion.id.group}:${it.name}" }
            check(resolved.none { it == "org.eclipse:yasson" }) {
                "serde-jsonb must not resolve org.eclipse:yasson"
            }
        }
    }
}
