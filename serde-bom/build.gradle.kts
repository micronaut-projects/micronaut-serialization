plugins {
    id("io.micronaut.build.internal.bom")
}

micronautBom {
    excludeProject.set({
        it.path.contains("doc-examples")
    })
}

dependencies {
    constraints {
        api(libs.bson)
        api("jakarta.json:jakarta.json-api:2.0.1")
        api("jakarta.json.bind:jakarta.json.bind-api:2.0.0")
    }
}

