plugins {
    id("java")
    id("io.micronaut.build.internal.serde-native-examples")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
}

dependencies {
    annotationProcessor(projects.micronautSerdeProcessor)

    implementation(projects.micronautSerdeJackson)
    implementation(mn.micronaut.http.client)
    implementation(libs.oci.aidocument)

    runtimeOnly(mnLogging.logback.classic)

    testImplementation(mnTest.micronaut.test.junit5)
}

application {
    mainClass.set("example.Application")
}
//
//tasks {
//    compileJava {
//        options.isFork = true
//        options.forkOptions.jvmArgs = listOf("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
//    }
//}
