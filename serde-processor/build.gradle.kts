plugins {
    id("io.micronaut.build.internal.module")
}

dependencies {
    implementation(projects.serdeApi)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mn.micronaut.inject.java.test)
    testCompileOnly(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.test.spock)
    if (!JavaVersion.current().isJava9Compatible()) {
        testImplementation(files(org.gradle.internal.jvm.Jvm.current().toolsJar))
    }
}
