plugins {
    id("io.micronaut.build.internal.serde-module")
}

configurations.named("testRuntimeClasspath") {
    resolutionStrategy.dependencySubstitution {
        substitute(module("io.micronaut:micronaut-jackson-databind"))
            .using(project(":serde-jackson")).because("we want to test Micronaut without jackson databind")
    }
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(mn.micronaut.graal)
    annotationProcessor(projects.serdeProcessor)
    annotationProcessor("io.micronaut.docs:micronaut-docs-asciidoc-config-props:2.0.0")

    compileOnly(mn.micronaut.management)
    api(projects.serdeApi)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.serdeProcessor)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.spock)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.management)
    testImplementation(mn.micronaut.jackson.databind)
    testImplementation(libs.jetbrains.annotations)
}
