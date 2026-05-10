plugins {
    id("io.micronaut.build.internal.serde-module")
}

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)


    api(mn.micronaut.context)
    api(projects.micronautSerdeApi)

    implementation(projects.micronautSerdeSupport)

    implementation(mn.jackson.dataformat.xml)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(projects.micronautSerdeXmlTck)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.junit.jupiter.engine)

}

tasks {
    test {
        useJUnitPlatform()
    }
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("5.0.0")
    }
}
