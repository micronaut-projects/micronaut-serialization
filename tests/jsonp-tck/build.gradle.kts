plugins {
    id("io.micronaut.build.internal.serde-base")
    id("java")
}

val jakartaJsonpTck by configurations.creating
val jakartaJsonpTckClasses = layout.buildDirectory.dir("jakartaJsonpTck/classes")
val jakartaJsonpTckJimage = layout.buildDirectory.dir("jakartaJsonpTck/jimage")
val jakartaJsonpTckSigtestClasspath = files(
    sourceSets.test.get().runtimeClasspath,
    jakartaJsonpTck,
    jakartaJsonpTckClasses,
    jakartaJsonpTckJimage.map { it.dir("java.base") }
)

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
    testImplementation(platform(mn.micronaut.core.bom))
    testImplementation(projects.micronautSerdeJsonpImpl)
    testImplementation(libs.managed.jakarta.json.api)
    testImplementation(mnTest.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(mnLogging.logback.classic)
    jakartaJsonpTck(libs.jakarta.json.tck)
    jakartaJsonpTck(libs.jakarta.tck.sigtest)
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
