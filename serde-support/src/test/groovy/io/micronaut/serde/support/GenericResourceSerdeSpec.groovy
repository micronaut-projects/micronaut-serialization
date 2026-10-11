package io.micronaut.serde.support

import io.micronaut.core.type.Argument
import io.micronaut.http.hateoas.GenericResource
import io.micronaut.http.hateoas.Link
import io.micronaut.http.hateoas.Resource
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class GenericResourceSerdeSpec extends Specification {

    @Inject
    ObjectMapper jsonMapper

    void "test write GenericResource with additional properties, links and embedded resources"() {
        given:
        GenericResource book = new GenericResource()
        book.addProperty("title", "Dune")
        book.link(Link.SELF, Link.of("/books/1"))
        GenericResource author = new GenericResource()
        author.addProperty("name", "Frank Herbert")
        author.addProperty("born", 1920)
        author.addProperty("alias", null)
        author.addProperty("tags", ["sf", "classic"])
        author.link(Link.SELF, Link.of("/authors/7"))
        author.embedded("books", book)

        expect:
        jsonMapper.writeValueAsString(author) == '{"name":"Frank Herbert","born":1920,"alias":null,"tags":["sf","classic"],' +
            '"_links":{"self":[{"href":"/authors/7","templated":false}]},' +
            '"_embedded":{"books":[{"title":"Dune","_links":{"self":[{"href":"/books/1","templated":false}]}}]}}'
    }

    void "test write an empty GenericResource"() {
        expect:
        jsonMapper.writeValueAsString(new GenericResource()) == '{}'
    }

    void "test read GenericResource with additional properties, links and embedded resources"() {
        when:
        GenericResource author = jsonMapper.readValue('{"name":"Frank Herbert","born":1920,"alias":null,"tags":["sf","classic"],' +
            '"_links":{"self":[{"href":"/authors/7","templated":false}],"books":{"href":"/authors/7/books{?page}","templated":true}},' +
            '"_embedded":{"books":[{"title":"Dune","_links":{"self":[{"href":"/books/1"}]}}]}}', GenericResource)

        then:
        author.additionalProperties == [name: "Frank Herbert", born: 1920, alias: null, tags: ["sf", "classic"]]
        author.links.get(Link.SELF).get()*.href == ["/authors/7"]
        author.links.get("books").get()*.href == ["/authors/7/books{?page}"]
        author.links.get("books").get()*.templated == [true]

        when:
        List<Resource> books = author.embedded.get("books").get()

        then:
        books.size() == 1
        books[0] instanceof GenericResource
        (books[0] as GenericResource).additionalProperties == [title: "Dune"]
        books[0].links.get(Link.SELF).get()*.href == ["/books/1"]
    }

    void "test GenericResource round trip"() {
        given:
        GenericResource book = new GenericResource()
        book.addProperty("title", "Dune")
        GenericResource author = new GenericResource()
        author.addProperty("name", "Frank Herbert")
        author.link(Link.SELF, Link.of("/authors/7"))
        author.embedded("books", book)

        when:
        String json = jsonMapper.writeValueAsString(author)
        GenericResource read = jsonMapper.readValue(json, GenericResource)

        then: // links do not implement equals
        read.additionalProperties == author.additionalProperties
        read.embedded.get("books").get() == [book]
        jsonMapper.writeValueAsString(read) == json
    }

    void "test read a Resource-typed value as a GenericResource"() {
        // the deserializer of CustomResource, a subtype of Resource, must not be picked for Resource
        when:
        Resource resource = jsonMapper.readValue('{"name":"Frank Herbert","_links":{"self":[{"href":"/authors/7"}]}}', Resource)

        then:
        resource instanceof GenericResource
        (resource as GenericResource).additionalProperties == [name: "Frank Herbert"]
        resource.links.get(Link.SELF).get()*.href == ["/authors/7"]

        when:
        ResourceHolder holder = jsonMapper.readValue('{"name":"holder","resource":{"name":"Frank Herbert"}}', ResourceHolder)

        then:
        holder.name() == "holder"
        holder.resource() instanceof GenericResource
        (holder.resource() as GenericResource).additionalProperties == [name: "Frank Herbert"]

        when:
        holder = jsonMapper.readValue('{"name":"holder","resource":null}', ResourceHolder)

        then:
        holder.resource() == null
    }

    void "test read / write a bean with a Map<String, List<Resource>> property"() {
        when:
        ResourcesByRel bean = jsonMapper.readValue('{"resources":{"authors":[{"name":"Frank Herbert"},{"name":"Isaac Asimov","_links":{"self":{"href":"/authors/8"}}}]}}', ResourcesByRel)

        then:
        List<Resource> authors = bean.resources().get("authors")
        authors.size() == 2
        authors.every { it instanceof GenericResource }
        authors.collect { (it as GenericResource).additionalProperties.name } == ["Frank Herbert", "Isaac Asimov"]
        authors[1].links.get(Link.SELF).get()*.href == ["/authors/8"]

        when:
        String json = jsonMapper.writeValueAsString(bean)

        then:
        json == '{"resources":{"authors":[{"name":"Frank Herbert"},' +
            '{"name":"Isaac Asimov","_links":{"self":[{"href":"/authors/8","templated":false}]}}]}}'
    }

    void "test read a list of resources"() {
        when:
        List<Resource> resources = jsonMapper.readValue('[{"name":"Frank Herbert"}]', Argument.listOf(Resource))

        then:
        resources.size() == 1
        (resources[0] as GenericResource).additionalProperties == [name: "Frank Herbert"]
    }
}
