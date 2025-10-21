import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("io.micronaut.build.internal.serde-examples")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
}

dependencies {
    kapt(projects.micronautSerdeProcessor)

    implementation(projects.micronautSerdeJackson)
    implementation(mn.micronaut.http.client)

    runtimeOnly(mnLogging.logback.classic)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.junit.platform.launcher)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}
