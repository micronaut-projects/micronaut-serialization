import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("io.micronaut.build.internal.serde-module")
}

val jacocoClassExcludes = listOf(
    "**/*\$Definition*",
    "**/*\$Reference*",
    "**/*\$Introspection*",
    "**/*Spec.class",
    "**/*Spec\$*.class"
)

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
    testImplementation(mn.micronaut.inject.kotlin)
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
    testImplementation(mn.jackson.dataformat.xml)
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isEnabled = true
    }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))

    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes")) {
            include("**/*.class")
            exclude(jacocoClassExcludes)
        }
    )
    sourceDirectories.setFrom(
        files(
            "src/main/java",
            "src/main/groovy",
            "src/test/java",
            "src/test/groovy",
            layout.buildDirectory.dir("generated/sources/annotationProcessor/java/main"),
            layout.buildDirectory.dir("generated/sources/annotationProcessor/java/test"),
            layout.buildDirectory.dir("generated/sources/annotationProcessor/groovy/main"),
            layout.buildDirectory.dir("generated/sources/annotationProcessor/groovy/test")
        )
    )

    reports {
        xml.required = true
        csv.required = true
        html.required = true
    }
}
