plugins {
    id("io.micronaut.build.internal.module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(project(":serde-processor"))

    api(mn.micronaut.jackson.core)
    api(mn.jackson.annotations)
    api(mn.micronaut.context)
    api(project(":serde-api"))

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(project(":serde-processor"))
    testImplementation(mn.micronaut.inject.java.test)
    testCompileOnly(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.test.spock)

    if (!JavaVersion.current().isJava9Compatible()) {
        testImplementation(files(org.gradle.internal.jvm.Jvm.current().toolsJar))
    }
}