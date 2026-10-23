plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)

    api(mn.micronaut.context)
    api(mn.micronaut.json.core)
    // Application-generated sources must resolve the retained generated-code marker.
    api(libs.micronaut.sourcegen.annotations)

    testImplementation(mnTest.micronaut.test.spock)
}

tasks {
    test {
        systemProperty("micronaut.databind", "generated")
    }

//    compileJava {
//        options.isFork = true
//        options.forkOptions.jvmArgs = listOf("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
//    }
}
