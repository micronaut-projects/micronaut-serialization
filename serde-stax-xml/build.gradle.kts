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

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(projects.micronautSerdeXmlTck)
    testCompileOnly(mn.jackson.dataformat.xml) {
        exclude(group = "com.fasterxml.woodstox", module = "woodstox-core")
        exclude(group = "org.codehaus.woodstox", module = "stax2-api")
    }
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
        enabledAfter("5.2.0")
    }
}
