plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.10"
    id("org.jetbrains.kotlin.kapt") version "1.6.10"
    id("org.jetbrains.kotlin.plugin.allopen") version "1.6.10"
    id("io.micronaut.application") version "3.2.2"
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
    implementation(libs.micronaut.http.client)
    runtimeOnly(libs.logback.classic)
    testImplementation(libs.micronaut.test.junit5)
}
application {
    mainClass.set("example.ApplicationKt")
}
