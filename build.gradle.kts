import org.sonarqube.gradle.SonarExtension

plugins {
    id("io.micronaut.build.internal.parent")
}

repositories {
    mavenCentral()
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
