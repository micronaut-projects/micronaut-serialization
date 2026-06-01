import org.sonarqube.gradle.SonarExtension

plugins {
    id("io.micronaut.build.internal.parent")
}

repositories {
    mavenCentral()
}

tasks.register("jakartaJsonpTck") {
    group = "verification"
    description = "Runs the standalone Jakarta JSON-P TCK module."
    dependsOn(":micronaut-tests:micronaut-jsonp-tck:jakartaJsonpTck")
}

tasks.register("jakartaJsonbTck") {
    group = "verification"
    description = "Runs the standalone Jakarta JSON-B TCK module."
    dependsOn(":micronaut-tests:micronaut-jsonb-tck:jakartaJsonbTck")
}

tasks.register("jakartaJsonTck") {
    group = "verification"
    description = "Runs the standalone Jakarta JSON-P and JSON-B TCK modules."
    dependsOn("jakartaJsonpTck", "jakartaJsonbTck")
}

if (System.getenv("SONAR_TOKEN") != null) {
    configure<SonarExtension> {
        properties {
            property("sonar.exclusions", "**/example/**")
            property(
                "sonar.cpd.exclusions",
                "serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/beans/**," +
                    "serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/records/**"
            )
        }
    }
}
