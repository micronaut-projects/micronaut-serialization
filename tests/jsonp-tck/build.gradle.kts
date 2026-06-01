plugins {
    id("io.micronaut.build.internal.serde-base")
    id("java")
}

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

val jakartaJsonpTck by configurations.creating
val jakartaJsonpTckClasses = layout.buildDirectory.dir("jakartaJsonpTck/classes")
val jakartaJsonpTckJimage = layout.buildDirectory.dir("jakartaJsonpTck/jimage")
val jakartaJsonpKnownFailuresFile = layout.projectDirectory.file("src/test/resources/known-failures.xml")
val jakartaJsonpKnownFailureResults = layout.buildDirectory.dir("test-results/jakartaJsonpTckKnownFailures")
val jakartaJsonpTckSigtestClasspath = files(
    sourceSets.test.get().runtimeClasspath,
    jakartaJsonpTck,
    jakartaJsonpTckClasses,
    jakartaJsonpTckJimage.map { it.dir("java.base") }
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
                appendLine("Known JSON-P TCK failures unexpectedly passed:")
                unexpectedlyPassing.sortedWith(compareBy(TckFailure::classname, TckFailure::name))
                    .forEach { appendLine(" - ${it.classname}#${it.name}") }
            }
            if (untrackedFailures.isNotEmpty()) {
                appendLine("Untracked JSON-P TCK failures:")
                untrackedFailures.sortedWith(compareBy(TckFailure::classname, TckFailure::name))
                    .forEach { appendLine(" - ${it.classname}#${it.name}") }
            }
        }
        throw GradleException(message)
    }
}

val unpackJakartaJsonpTck by tasks.registering(Sync::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        jakartaJsonpTck.resolvedConfiguration.resolvedArtifacts
            .filter { it.moduleVersion.id.name == "jakarta.json-tck-tests" }
            .map { zipTree(it.file) }
    })
    into(jakartaJsonpTckClasses)
}

dependencies {
    testImplementation(projects.micronautSerdeJsonpImpl)
    testImplementation(libs.managed.jakarta.json.api)
    testImplementation(mnTest.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(mnLogging.logback.classic)
    jakartaJsonpTck(libs.managed.jakarta.json.tck)
    jakartaJsonpTck(libs.managed.jakarta.tck.sigtest)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Test>("jakartaJsonpTck") {
    group = "verification"
    description = "Runs the standalone Jakarta JSON-P TCK."
    useJUnitPlatform()
    dependsOn(unpackJakartaJsonpTck)
    classpath = sourceSets.test.get().runtimeClasspath + jakartaJsonpTck + files(jakartaJsonpTckClasses)
    testClassesDirs = files(jakartaJsonpTckClasses)
    systemProperty("jakarta.json.provider", "io.micronaut.serde.jsonp.MicronautJsonProvider")
    systemProperty("jimage.dir", jakartaJsonpTckJimage.get().asFile.absolutePath)
    systemProperty("sigTestClasspath", jakartaJsonpTckSigtestClasspath.asPath)
    systemProperty("signature.sigTestClasspath", jakartaJsonpTckSigtestClasspath.asPath)
    doFirst {
        val jimageDir = jakartaJsonpTckJimage.get().asFile
        jimageDir.mkdirs()
    }
}

tasks.register<Test>("jakartaJsonpTckSingle") {
    group = "verification"
    description = "Runs one Jakarta JSON-P TCK test selected with -PjakartaJsonpTckTest=<pattern>."
    useJUnitPlatform()
    dependsOn(unpackJakartaJsonpTck)
    classpath = tasks.named<Test>("jakartaJsonpTck").get().classpath
    testClassesDirs = tasks.named<Test>("jakartaJsonpTck").get().testClassesDirs
    val selectedTest = providers.gradleProperty("jakartaJsonpTckTest")
    onlyIf { selectedTest.isPresent }
    filter {
        selectedTest.orNull?.let { includeTestsMatching(it) }
    }
}

tasks.register<Test>("jakartaJsonpTckKnownFailures") {
    group = "verification"
    description = "Runs the standalone Jakarta JSON-P TCK and compares failures to the known-failure tracker."
    useJUnitPlatform()
    dependsOn(unpackJakartaJsonpTck)
    classpath = tasks.named<Test>("jakartaJsonpTck").get().classpath
    testClassesDirs = tasks.named<Test>("jakartaJsonpTck").get().testClassesDirs
    ignoreFailures = true
    reports.junitXml.outputLocation = jakartaJsonpKnownFailureResults
    systemProperty("jakarta.json.provider", "io.micronaut.serde.jsonp.MicronautJsonProvider")
    systemProperty("jimage.dir", jakartaJsonpTckJimage.get().asFile.absolutePath)
    systemProperty("sigTestClasspath", jakartaJsonpTckSigtestClasspath.asPath)
    systemProperty("signature.sigTestClasspath", jakartaJsonpTckSigtestClasspath.asPath)
    doFirst {
        jakartaJsonpTckJimage.get().asFile.mkdirs()
    }
    finalizedBy("checkJakartaJsonpTckKnownFailures")
}

tasks.register<Test>("discoverJakartaJsonpTckKnownFailureResults") {
    group = "verification"
    description = "Runs the standalone Jakarta JSON-P TCK for known-failure discovery without applying the known-failure guard."
    useJUnitPlatform()
    dependsOn(unpackJakartaJsonpTck)
    classpath = tasks.named<Test>("jakartaJsonpTck").get().classpath
    testClassesDirs = tasks.named<Test>("jakartaJsonpTck").get().testClassesDirs
    ignoreFailures = true
    reports.junitXml.outputLocation = jakartaJsonpKnownFailureResults
    systemProperty("jakarta.json.provider", "io.micronaut.serde.jsonp.MicronautJsonProvider")
    systemProperty("jimage.dir", jakartaJsonpTckJimage.get().asFile.absolutePath)
    systemProperty("sigTestClasspath", jakartaJsonpTckSigtestClasspath.asPath)
    systemProperty("signature.sigTestClasspath", jakartaJsonpTckSigtestClasspath.asPath)
    doFirst {
        jakartaJsonpTckJimage.get().asFile.mkdirs()
    }
}

tasks.register("checkJakartaJsonpTckKnownFailures") {
    group = "verification"
    description = "Fails when Jakarta JSON-P TCK known failures unexpectedly pass or untracked failures appear."
    inputs.file(jakartaJsonpKnownFailuresFile)
    inputs.dir(jakartaJsonpKnownFailureResults)
    doLast {
        checkKnownFailures(jakartaJsonpKnownFailuresFile.asFile, jakartaJsonpKnownFailureResults.get().asFile)
    }
}

tasks.register("refreshJakartaJsonpTckKnownFailures") {
    group = "verification"
    description = "Refreshes the Jakarta JSON-P TCK known-failure XML from the current known-failure run."
    dependsOn("discoverJakartaJsonpTckKnownFailureResults")
    doLast {
        writeKnownFailures(
            jakartaJsonpKnownFailuresFile.asFile,
            "jakarta-jsonp",
            actualFailures(jakartaJsonpKnownFailureResults.get().asFile)
        )
    }
}

tasks.register("discoverJakartaJsonpTckKnownFailures") {
    group = "verification"
    description = "Discovers current Jakarta JSON-P TCK failures and writes the known-failure XML."
    dependsOn("refreshJakartaJsonpTckKnownFailures")
}
