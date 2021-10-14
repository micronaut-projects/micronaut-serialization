plugins {
    id("io.micronaut.build.internal.module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    api(mn.micronaut.context)
    api(mn.micronaut.http)
    api(mn.micronaut.jackson.core)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(mn.micronaut.inject.groovy)
    testAnnotationProcessor(project(":serialization-generator"))

    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.groovy.test)
    testImplementation(project(":serialization-generator"))

    if (!JavaVersion.current().isJava9Compatible()) {
        testImplementation(files(org.gradle.internal.jvm.Jvm.current().toolsJar))
    }
}

tasks {
    test {
        systemProperty("micronaut.databind", "generated")
    }
}
