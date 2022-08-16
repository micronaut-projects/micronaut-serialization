plugins {
    id("io.micronaut.build.internal.module")
}

repositories {
    maven { setUrl("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
    mavenCentral()
}

dependencies {
    annotationProcessor(mn.micronaut.inject.java)

    api(mn.micronaut.context)
    api(mn.micronaut.json.core)
}

tasks {
    test {
        systemProperty("micronaut.databind", "generated")
    }

//    compileJava {
//        options.isFork = true
//        options.forkOptions.jvmArgs = listOf("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005")
//    }
}
