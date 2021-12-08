plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    implementation("com.squareup:javapoet:1.13.0")
    implementation(mn.jackson.annotations)
    implementation(mn.micronaut.inject.java)
    implementation(mn.micronaut.inject.groovy)
    implementation(projects.serdeApi)
    implementation(projects.serdeSupport)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.serdeProcessor)
    testImplementation(projects.serdeProcessor)
    testImplementation(projects.serdeTck)
    testImplementation(mn.micronaut.inject.java.test)
    testCompileOnly(mn.micronaut.inject.groovy)
    testImplementation(mn.micronaut.test.spock)
    testImplementation(mn.jackson.annotations)
    if (!JavaVersion.current().isJava9Compatible()) {
        testImplementation(files(org.gradle.internal.jvm.Jvm.current().toolsJar))
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}