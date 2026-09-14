plugins {
    id ("java")
    id ("io.micronaut.build.internal.serde-base")
    id ("org.graalvm.buildtools.native")
}

dependencies {

    testImplementation(mn.micronaut.http.server.tck)
    testImplementation(projects.micronautSerdeJackson)
    testRuntimeOnly(mnLogging.logback.classic)

    testAnnotationProcessor(platform(mn.micronaut.core.bom))
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(mnValidation.micronaut.validation.processor)

    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(libs.junit.platform.engine)
    testImplementation(mnTest.junit.jupiter.engine)
    testImplementation(mnValidation.micronaut.validation)
}

tasks.withType<Test> {
    useJUnitPlatform()
    // NettyHttpServer.getHost() falls back to $HOSTNAME before "localhost". Shells on many Linux
    // distros export HOSTNAME (macOS/Windows do not), which makes the server advertise the machine
    // name, so the TCK client connects over a non-loopback interface and the remote-address and
    // CORS isHostLocal assertions fail. Pin the host so the suite behaves the same everywhere.
    systemProperty("micronaut.server.host", "localhost")
}
