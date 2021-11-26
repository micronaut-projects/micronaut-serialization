plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor("io.micronaut.docs:micronaut-docs-asciidoc-config-props:2.0.0.RC1")
    api(mn.micronaut.context)
    api(mn.micronaut.json.core)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.inject.groovy.test)
    testImplementation(mn.micronaut.jackson.core)
    testImplementation(mn.micronaut.reactor)

    if (!JavaVersion.current().isJava9Compatible()) {
        testImplementation(files(org.gradle.internal.jvm.Jvm.current().toolsJar))
    }
}

tasks {
    test {
        systemProperty("micronaut.databind", "generated")
    }

//    compileJava {
//        options.isFork = true
//        options.forkOptions.jvmArgs = listOf("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
//    }
}
