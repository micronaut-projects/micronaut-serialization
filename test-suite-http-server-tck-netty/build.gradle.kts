plugins {
    id ("java")
    id ("io.micronaut.build.internal.serde-base")
    id ("org.graalvm.buildtools.native")
}

dependencies {

    testImplementation(mn.micronaut.http.server.tck)
    testImplementation(projects.micronautSerdeJackson)
    testRuntimeOnly(mnLogging.logback.classic)

    testAnnotationProcessor(platform(mn.micronaut.core.bom))
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(mnValidation.micronaut.validation.processor)

    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(libs.junit.platform.engine)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(mnValidation.micronaut.validation)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

graalvmNative {
    toolchainDetection = false
    binaries {
        all {
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
    metadataRepository {
        enabled = true
    }
}
