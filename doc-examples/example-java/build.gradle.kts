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
    implementation(projects.micronautSerdeJackson)
    implementation(mn.micronaut.http.client)
    runtimeOnly(mn.logback.classic)
    testImplementation(mnTest.micronaut.test.junit5)
}
application {
    mainClass.set("example.Application")
}

//tasks {
//    compileJava {
//        options.isFork = true
//        options.forkOptions.jvmArgs = listOf("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
//    }
//}
