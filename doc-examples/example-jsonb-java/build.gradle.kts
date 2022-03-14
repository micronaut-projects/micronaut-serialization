plugins {
    id("java")
    id("io.micronaut.application") version "3.2.2"
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
}
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("io.micronaut:micronaut-jackson-databind"))
            .using(project(":serde-jsonp"))
    }
}
dependencies {
    annotationProcessor(projects.serdeProcessor)
    implementation("jakarta.json.bind:jakarta.json.bind-api:2.0.0")
    implementation("io.micronaut:micronaut-http-client")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation("io.micronaut.test:micronaut-test-junit5:3.1.1")
}
application {
    mainClass.set("example.Application")
}
