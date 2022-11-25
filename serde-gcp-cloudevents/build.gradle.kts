plugins {
    id("io.micronaut.build.internal.module")
}

dependencies {
    annotationProcessor(projects.serdeProcessor)

    implementation(projects.serdeSupport)
    api(libs.managed.google.cloudevent.types)

    testImplementation(projects.serdeJackson)
}

micronautBuild {
    binaryCompatibility {
        enabled.set(false)
    }
}
