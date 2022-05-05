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
    implementation(libs.jakarta.json.bind.api)
    implementation(libs.micronaut.http.client)
    runtimeOnly(libs.logback.classic)
    testImplementation(libs.micronaut.test.junit5)
}
application {
    mainClass.set("example.Application")
}
