plugins {
    id("io.micronaut.build.internal.serde-base")
    id("java")
}

import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

val jakartaJsonbTck by configurations.creating
val jakartaJsonbTckClasses = layout.buildDirectory.dir("jakartaJsonbTck/classes")
val jakartaJsonbTckJimage = layout.buildDirectory.dir("jakartaJsonbTck/jimage")
val jakartaJsonbTckRuntimeClasspath = jakartaJsonbTck.filter {
    !it.name.startsWith("jakarta.json.bind-tck-")
}
val jakartaJsonbKnownFailuresFile = layout.projectDirectory.file("src/test/resources/known-failures.xml")
val jakartaJsonbKnownFailureResults = layout.buildDirectory.dir("test-results/jakartaJsonbTckKnownFailures")
val jakartaJsonbTckSigtestClasspath = files(
    sourceSets.test.get().runtimeClasspath,
    jakartaJsonbTck,
    jakartaJsonbTckClasses,
    jakartaJsonbTckJimage.map { it.dir("java.base") }
)

data class TckFailure(val classname: String, val name: String)

fun parseXml(file: File) = DocumentBuilderFactory.newInstance()
    .newDocumentBuilder()
    .parse(file)

fun knownFailures(file: File): Set<TckFailure> {
    if (!file.isFile) {
        return emptySet()
    }
    val nodes = parseXml(file).getElementsByTagName("testcase")
    return (0 until nodes.length)
        .map { nodes.item(it) as Element }
        .map { TckFailure(it.getAttribute("classname"), it.getAttribute("name")) }
        .toSet()
}

fun actualFailures(resultsDir: File): Set<TckFailure> {
    if (!resultsDir.isDirectory) {
        return emptySet()
    }
    return resultsDir.walkTopDown()
        .filter { it.isFile && it.extension == "xml" }
        .flatMap { file ->
            val nodes = parseXml(file).getElementsByTagName("testcase")
            (0 until nodes.length)
                .map { nodes.item(it) as Element }
                .filter {
                    it.getElementsByTagName("failure").length > 0 ||
                        it.getElementsByTagName("error").length > 0
                }
                .map { TckFailure(it.getAttribute("classname"), it.getAttribute("name")) }
        }
        .toSet()
}

fun xmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

fun writeKnownFailures(file: File, suite: String, failures: Set<TckFailure>) {
    file.parentFile.mkdirs()
    file.writeText(
        buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<known-failures suite="$suite">""")
            failures.sortedWith(compareBy(TckFailure::classname, TckFailure::name)).forEach {
                appendLine("""  <testcase classname="${xmlEscape(it.classname)}" name="${xmlEscape(it.name)}"/>""")
            }
            appendLine("</known-failures>")
        }
    )
}

fun checkKnownFailures(knownFile: File, resultsDir: File) {
    val expected = knownFailures(knownFile)
    val actual = actualFailures(resultsDir)
    val unexpectedlyPassing = expected - actual
    val untrackedFailures = actual - expected
    if (unexpectedlyPassing.isNotEmpty() || untrackedFailures.isNotEmpty()) {
        val message = buildString {
            if (unexpectedlyPassing.isNotEmpty()) {
                appendLine("Known JSON-B TCK failures unexpectedly passed:")
                unexpectedlyPassing.sortedWith(compareBy(TckFailure::classname, TckFailure::name))
                    .forEach { appendLine(" - ${it.classname}#${it.name}") }
            }
            if (untrackedFailures.isNotEmpty()) {
                appendLine("Untracked JSON-B TCK failures:")
                untrackedFailures.sortedWith(compareBy(TckFailure::classname, TckFailure::name))
                    .forEach { appendLine(" - ${it.classname}#${it.name}") }
            }
        }
        throw GradleException(message)
    }
}

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
    testCompileOnly(libs.managed.jakarta.json.bind.tck)
    testRuntimeOnly(libs.managed.jakarta.enterprise.cdi.api)
    testRuntimeOnly(libs.managed.weld.se.core)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(mnLogging.logback.classic)
    jakartaJsonbTck(libs.managed.jakarta.json.bind.tck)
    jakartaJsonbTck(libs.managed.jakarta.tck.sigtest)
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

tasks.register<Test>("jakartaJsonbTckKnownFailures") {
    group = "verification"
    description = "Runs the standalone Jakarta JSON-B TCK and compares failures to the known-failure tracker."
    useJUnitPlatform()
    dependsOn(unpackJakartaJsonbTck)
    classpath = tasks.named<Test>("jakartaJsonbTck").get().classpath
    testClassesDirs = tasks.named<Test>("jakartaJsonbTck").get().testClassesDirs
    ignoreFailures = true
    reports.junitXml.outputLocation = jakartaJsonbKnownFailureResults
    systemProperty("jakarta.json.bind.provider", "io.micronaut.serde.jsonb.MicronautJsonbReflectionProvider")
    systemProperty("jakarta.json.provider", "io.micronaut.serde.jsonp.MicronautJsonProvider")
    systemProperty("jimage.dir", jakartaJsonbTckJimage.get().asFile.absolutePath)
    systemProperty("sigTestClasspath", jakartaJsonbTckSigtestClasspath.asPath)
    systemProperty("signature.sigTestClasspath", jakartaJsonbTckSigtestClasspath.asPath)
    doFirst {
        jakartaJsonbTckJimage.get().asFile.mkdirs()
    }
    finalizedBy("checkJakartaJsonbTckKnownFailures")
}

tasks.register<Test>("discoverJakartaJsonbTckKnownFailureResults") {
    group = "verification"
    description = "Runs the standalone Jakarta JSON-B TCK for known-failure discovery without applying the known-failure guard."
    useJUnitPlatform()
    dependsOn(unpackJakartaJsonbTck)
    classpath = tasks.named<Test>("jakartaJsonbTck").get().classpath
    testClassesDirs = tasks.named<Test>("jakartaJsonbTck").get().testClassesDirs
    ignoreFailures = true
    reports.junitXml.outputLocation = jakartaJsonbKnownFailureResults
    systemProperty("jakarta.json.bind.provider", "io.micronaut.serde.jsonb.MicronautJsonbReflectionProvider")
    systemProperty("jakarta.json.provider", "io.micronaut.serde.jsonp.MicronautJsonProvider")
    systemProperty("jimage.dir", jakartaJsonbTckJimage.get().asFile.absolutePath)
    systemProperty("sigTestClasspath", jakartaJsonbTckSigtestClasspath.asPath)
    systemProperty("signature.sigTestClasspath", jakartaJsonbTckSigtestClasspath.asPath)
    doFirst {
        jakartaJsonbTckJimage.get().asFile.mkdirs()
    }
}

tasks.register("checkJakartaJsonbTckKnownFailures") {
    group = "verification"
    description = "Fails when Jakarta JSON-B TCK known failures unexpectedly pass or untracked failures appear."
    inputs.file(jakartaJsonbKnownFailuresFile)
    inputs.dir(jakartaJsonbKnownFailureResults)
    doLast {
        checkKnownFailures(jakartaJsonbKnownFailuresFile.asFile, jakartaJsonbKnownFailureResults.get().asFile)
    }
}

tasks.register("refreshJakartaJsonbTckKnownFailures") {
    group = "verification"
    description = "Refreshes the Jakarta JSON-B TCK known-failure XML from the current known-failure run."
    dependsOn("discoverJakartaJsonbTckKnownFailureResults")
    doLast {
        writeKnownFailures(
            jakartaJsonbKnownFailuresFile.asFile,
            "jakarta-jsonb",
            actualFailures(jakartaJsonbKnownFailureResults.get().asFile)
        )
    }
}

tasks.register("discoverJakartaJsonbTckKnownFailures") {
    group = "verification"
    description = "Discovers current Jakarta JSON-B TCK failures and writes the known-failure XML."
    dependsOn("refreshJakartaJsonbTckKnownFailures")
}
