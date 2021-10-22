plugins {
    id("io.micronaut.build.internal.module")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)

    api(mn.micronaut.context)
    api(mn.micronaut.http)
    api(project(":serde-api"))

    testImplementation(mn.micronaut.inject.java.test)

}