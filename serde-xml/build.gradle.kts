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

    implementation(mn.jackson.dataformat.xml) {
        // woodstox is self closing tag behavior by default
//        exclude(group = "com.fasterxml.woodstox", module = "woodstox-core")
//        exclude(group = "org.codehaus.woodstox", module = "stax2-api")
    }

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testImplementation(projects.micronautSerdeProcessor)
//    testImplementation(projects.micronautSerdeTck)
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
