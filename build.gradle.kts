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
                "sonar.coverage.exclusions",
                "serde-api/src/main/java/io/micronaut/serde/ObjectMapper.java," +
                    "serde-api/src/main/java/io/micronaut/serde/ObjectMappers.java," +
                    "serde-api/src/main/java/io/micronaut/serde/SerdeRegistry.java," +
                    "serde-api/src/main/java/io/micronaut/serde/config/SerdeConfiguration.java," +
                    "serde-jackson/src/main/java/io/micronaut/serde/jackson/JacksonDecoder.java," +
                    "serde-jsonb/src/main/java/io/micronaut/serde/jsonb/**," +
                    "serde-jsonp/src/main/java/io/micronaut/serde/json/stream/**," +
                    "serde-jsonp-impl/src/main/java/io/micronaut/serde/jsonp/**," +
                    "serde-processor/src/main/java/io/micronaut/serde/processor/SerdeAnnotationVisitor.java," +
                    "serde-processor/src/main/java/io/micronaut/serde/processor/jsonb/**," +
                    "serde-support/src/main/java/io/micronaut/serde/support/deserializers/ObjectDeserializer.java," +
                    "serde-support/src/main/java/io/micronaut/serde/support/serializers/SerBean.java," +
                    "serde-support/src/main/java/io/micronaut/serde/support/serdes/**"
            )
            property(
                "sonar.cpd.exclusions",
                "serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/beans/**," +
                    "serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/records/**"
            )
        }
    }

    subprojects {
        if (name.contains("tck", ignoreCase = true)) {
            configure<SonarExtension> {
                setSkipProject(true)
            }
        }
    }
}
