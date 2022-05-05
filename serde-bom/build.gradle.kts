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
        api(libs.jakarta.json.api)
        api(libs.jakarta.json.bind.api)
    }
}

