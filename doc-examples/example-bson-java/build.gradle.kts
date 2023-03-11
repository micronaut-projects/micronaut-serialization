plugins {
    id("java")
    id("io.micronaut.build.internal.serde-examples")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
}
dependencies {
    annotationProcessor(projects.micronautSerdeProcessor)
    implementation(projects.micronautSerdeBson)
    implementation(mn.micronaut.http.client)
    runtimeOnly(mn.logback.classic)
    testImplementation(mnTest.micronaut.test.junit5)
}
application {
    mainClass.set("example.Application")
}
graalvmNative {
    binaries {
        named("main") {
            buildArgs.add("--trace-class-initialization=org.bson.BsonType")
        }
    }
}
