plugins {
    id("io.micronaut.build.internal.module")
}



configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("io.micronaut:micronaut-jackson-databind"))
            .using(project(":serde-jackson")).because("we want to test Micronaut without jackson databind")
    }
}


dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.serdeProcessor)
    annotationProcessor("io.micronaut.docs:micronaut-docs-asciidoc-config-props:2.0.0")

    api(projects.serdeApi)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.serdeProcessor)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mn.micronaut.test.spock)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.management)
}