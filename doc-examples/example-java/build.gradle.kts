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
    implementation(projects.micronautSerdeYaml)
    implementation(mn.micronaut.http.client)
    implementation(libs.oci.aidocument)

    runtimeOnly(mnLogging.logback.classic)

    testImplementation(mnTest.micronaut.test.junit5)
}

application {
    mainClass.set("example.Application")
}

graalvmNative {
    metadataRepository {
        // Jackson 2.21.2 is newer than the available repository metadata, which still references stale Databind types.
        excludedModules.add("com.fasterxml.jackson.core:jackson-databind")
    }
}
//
//tasks {
//    compileJava {
//        options.isFork = true
//        options.forkOptions.jvmArgs = listOf("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
//    }
//}
