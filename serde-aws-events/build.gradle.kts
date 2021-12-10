plugins {
    id("io.micronaut.build.internal.module")
}

dependencies {
    annotationProcessor(projects.serdeProcessor)
    annotationProcessor(projects.serdeJackson)
    implementation("com.amazonaws:aws-lambda-java-events:3.11.0")
}
