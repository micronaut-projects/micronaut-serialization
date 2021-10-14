plugins {
    id("io.micronaut.build.internal.module")
}
dependencies {
    annotationProcessor(mn.micronaut.inject.java)

    api(project(":serialization-api"))
    api(mn.micronaut.inject)
    api(mn.micronaut.inject.java)
    compileOnly(mn.micronaut.inject.groovy)

    api("com.squareup:javapoet:1.13.0")
    api(mn.jackson.core)

    testAnnotationProcessor(mn.micronaut.inject.java)
    
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.groovy.test)
    if (!JavaVersion.current().isJava9Compatible()) {
        testImplementation(files(org.gradle.internal.jvm.Jvm.current().toolsJar))
    }
}
