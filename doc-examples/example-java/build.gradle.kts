plugins {
    id("java")
    id("io.micronaut.application") version "3.1.1"
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
}
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("io.micronaut:micronaut-jackson-databind"))
            .using(project(":serde-jackson"))
    }
}
dependencies {
    annotationProcessor(projects.serdeProcessor)
    implementation("io.micronaut:micronaut-http-client")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation("io.micronaut.test:micronaut-test-junit5:3.0.5")
}
application {
    mainClass.set("example.Application")
}
