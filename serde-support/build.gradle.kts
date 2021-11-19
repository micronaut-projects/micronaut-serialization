plugins {
    id("io.micronaut.build.internal.serde-module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)

    api(mn.micronaut.context)
    api(mn.micronaut.http)
    api(projects.serdeApi)

    testImplementation(mn.micronaut.inject.java.test)

}