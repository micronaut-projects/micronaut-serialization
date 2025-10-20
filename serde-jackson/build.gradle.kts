import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    api(mn.micronaut.jackson.core)
    api(mn.jackson.annotations)
    api(mn.micronaut.context)
    api(projects.micronautSerdeApi)

    implementation(projects.micronautSerdeSupport)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testCompileOnly(mn.micronaut.inject.groovy)
    testImplementation(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(projects.micronautSerdeTck)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mn.micronaut.inject.kotlin.test)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.jackson.databind)
    testImplementation(mn.micronaut.management)
    testImplementation(libs.microstream.storage.restclient)
    testImplementation(libs.aws.lambda.serialization)
    testImplementation(libs.aws.lambda.events)
    testImplementation(libs.micronaut.discovery)
    testImplementation(projects.micronautSerdeJacksonTck)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.micronaut.http.server.netty)
}

val toolchainService = extensions.getByType<JavaToolchainService>()
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Run test JVM with Java 21 to avoid Kotlin compiler crash on Java 25 (IllegalArgumentException: 25)
    javaLauncher.set(toolchainService.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
}
