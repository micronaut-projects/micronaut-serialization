plugins {
    id("io.micronaut.build.internal.serde-module")
    id("antlr")
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)
    annotationProcessor(projects.micronautSerdeProcessor)

    api(mn.micronaut.json.core)
    api(projects.micronautSerdeApi)
    implementation(projects.micronautSerdeSupport)

    testRuntimeOnly(mn.snakeyaml)
    testRuntimeOnly(libs.junit.jupiter.engine)

    testImplementation(projects.micronautSerdeJackson)


    implementation(libs.antlr.runtime)
    antlr(libs.antlr)
}

tasks.named("generateGrammarSource", AntlrTask::class.java).configure {
    outputDirectory = file("$outputDirectory/io/micronaut/serde/jmespath/parser")
    arguments = listOf("-visitor", "-package", "io.micronaut.serde.jmespath.parser")
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(tasks.withType<AntlrTask>())
}

tasks.withType<Jar>().configureEach {
    dependsOn(tasks.withType<AntlrTask>())
}

tasks {
    test {
        useJUnitPlatform()
    }
}
