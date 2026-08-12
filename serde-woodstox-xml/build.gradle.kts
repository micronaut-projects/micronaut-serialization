plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    api(projects.micronautSerdeStaxXml)

    runtimeOnly(libs.woodstox.core)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testImplementation(projects.micronautSerdeProcessor)
    testImplementation(projects.micronautSerdeXmlTck)
    testCompileOnly(mn.jackson.dataformat.xml)
    testImplementation(mn.micronaut.inject.java.test)
    testImplementation(mnTest.micronaut.test.spock)
}

tasks {
    test {
        useJUnitPlatform()
    }
}

micronautBuild {
    binaryCompatibility {
        enabledAfter("3.2.0")
    }
}
