plugins {
    id("java")
    id("io.micronaut.build.internal.serde-examples")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
}
dependencies {
    annotationProcessor(projects.serdeProcessor)
    implementation(project(":serde-jsonp"))
    implementation(libs.managed.jakarta.json.bindApi)
    implementation("io.micronaut:micronaut-http-client")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
}
application {
    mainClass.set("example.Application")
}
