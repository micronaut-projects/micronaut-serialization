plugins {
    id("io.micronaut.build.internal.serde-base")
    id("java")
}

import java.util.zip.ZipFile

val jakartaJsonbTck by configurations.creating
val jakartaJsonbTckClasses = layout.buildDirectory.dir("jakartaJsonbTck/classes")
val jakartaJsonbTckJimage = layout.buildDirectory.dir("jakartaJsonbTck/jimage")
val jakartaJsonbTckRuntimeClasspath = jakartaJsonbTck.filter {
    !it.name.startsWith("jakarta.json.bind-tck-")
}
val jakartaJsonbTckSigtestClasspath = files(
    sourceSets.test.get().runtimeClasspath,
    jakartaJsonbTck,
    jakartaJsonbTckClasses,
    jakartaJsonbTckJimage.map { it.dir("java.base") }
)

val unpackJakartaJsonbTck by tasks.registering(Sync::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        jakartaJsonbTck.resolvedConfiguration.resolvedArtifacts
            .filter { it.moduleVersion.id.name == "jakarta.json.bind-tck" }
            .map { zipTree(it.file) }
    })
    into(jakartaJsonbTckClasses)
}

dependencies {
    testAnnotationProcessor(platform(mn.micronaut.core.bom))
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(projects.micronautSerdeProcessor)

    testImplementation(platform(mn.micronaut.core.bom))
    testCompileOnly(projects.micronautSerdeApi)
    testImplementation(projects.micronautSerdeJsonb)
    testImplementation(projects.micronautSerdeJsonpImpl)
    testImplementation(libs.managed.jakarta.json.bindApi)
    testImplementation(libs.managed.jakarta.json.api)
    testImplementation(mnTest.junit.jupiter.engine)
    testCompileOnly(libs.jakarta.json.bind.tck)
    testRuntimeOnly(libs.jakarta.enterprise.cdi.api)
    testRuntimeOnly(libs.weld.se.core)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(mnLogging.logback.classic)
    jakartaJsonbTck(libs.jakarta.json.bind.tck)
    jakartaJsonbTck(libs.jakarta.tck.sigtest)
}

val generateJakartaJsonbTckSerdeImports by tasks.registering {
    val outputDirectory = layout.buildDirectory.dir("generated/sources/jakartaJsonbTckSerdeImports/java")
    inputs.files(jakartaJsonbTck)
    outputs.dir(outputDirectory)
    doLast {
        val tckJars = jakartaJsonbTck.resolvedConfiguration.resolvedArtifacts
            .filter { it.moduleVersion.id.name == "jakarta.json.bind-tck" }
            .map { it.file }
        val packages = sortedSetOf<String>()
        val classImports = sortedSetOf<String>()
        tckJars.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith("ee/jakarta/tck/json/bind/") }
                    .filter { it.endsWith(".class") }
                    .filter { "/model/" in it }
                    .filterNot { it.endsWith("/package-info.class") }
                    .map { it.substringBeforeLast('/').replace('/', '.') }
                    .forEach(packages::add)
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith("ee/jakarta/tck/json/bind/defaultmapping/polymorphictypes/") }
                    .filter { it.endsWith(".class") }
                    .filter { '$' in it }
                    .filterNot { it.endsWith("/package-info.class") }
                    .map { it.removeSuffix(".class").replace('/', '.').replace('$', '.') }
                    .forEach(classImports::add)
            }
        }
        val outputFile = outputDirectory.get().file("io/micronaut/serde/jsonb/tck/JakartaJsonbTckSerdeImports.java").asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            buildString {
                appendLine("package io.micronaut.serde.jsonb.tck;")
                appendLine()
                appendLine("import io.micronaut.serde.annotation.SerdeImport;")
                appendLine()
                packages.forEach {
                    appendLine("@SerdeImport(packageName = \"$it\")")
                }
                classImports.forEach {
                    appendLine("@SerdeImport($it.class)")
                }
                appendLine("final class JakartaJsonbTckSerdeImports {")
                appendLine("    private JakartaJsonbTckSerdeImports() {")
                appendLine("    }")
                appendLine("}")
            }
        )
    }
}

sourceSets {
    test {
        java.srcDir(generateJakartaJsonbTckSerdeImports.map { layout.buildDirectory.dir("generated/sources/jakartaJsonbTckSerdeImports/java") })
    }
}

tasks.compileTestJava {
    dependsOn(generateJakartaJsonbTckSerdeImports)
}

tasks.test {
    useJUnitPlatform()
    exclude("io/micronaut/serde/jsonb/tck/**")
    failOnNoDiscoveredTests = false
}

tasks.register<Test>("jakartaJsonbTck") {
    group = "verification"
    description = "Runs the standalone Jakarta JSON-B TCK."
    useJUnitPlatform()
    dependsOn(unpackJakartaJsonbTck)
    classpath = sourceSets.test.get().runtimeClasspath + jakartaJsonbTckRuntimeClasspath + files(jakartaJsonbTckClasses)
    testClassesDirs = files(jakartaJsonbTckClasses)
    systemProperty("jakarta.json.bind.provider", "io.micronaut.serde.jsonb.MicronautJsonbReflectionProvider")
    systemProperty("jakarta.json.provider", "io.micronaut.serde.jsonp.MicronautJsonProvider")
    systemProperty("jimage.dir", jakartaJsonbTckJimage.get().asFile.absolutePath)
    systemProperty("sigTestClasspath", jakartaJsonbTckSigtestClasspath.asPath)
    systemProperty("signature.sigTestClasspath", jakartaJsonbTckSigtestClasspath.asPath)
    doFirst {
        val jimageDir = jakartaJsonbTckJimage.get().asFile
        jimageDir.mkdirs()
    }
}

tasks.register<Test>("jakartaJsonbTckSingle") {
    group = "verification"
    description = "Runs one Jakarta JSON-B TCK test selected with -PjakartaJsonbTckTest=<pattern>."
    useJUnitPlatform()
    dependsOn(unpackJakartaJsonbTck)
    classpath = tasks.named<Test>("jakartaJsonbTck").get().classpath
    testClassesDirs = tasks.named<Test>("jakartaJsonbTck").get().testClassesDirs
    val selectedTest = providers.gradleProperty("jakartaJsonbTckTest")
    onlyIf { selectedTest.isPresent }
    filter {
        selectedTest.orNull?.let { includeTestsMatching(it) }
    }
}
