package io.micronaut.serde.yaml

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.yaml.data.Book
import io.micronaut.serde.yaml.data.MutableBook
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class YamlObjectMapperSpec extends Specification {

    @Inject
    ApplicationContext context

    @Inject
    YamlObjectMapper mapper

    def "the mapper is available under the yaml qualifier"() {
        expect:
        context.getBean(ObjectMapper, Qualifiers.byName(YamlObjectMapper.YAML)) instanceof YamlObjectMapper
        context.getBean(JsonMapper, Qualifiers.byName(YamlObjectMapper.YAML)) instanceof YamlObjectMapper
        mapper.serdeRegistry != null
    }

    def "writeValueToTree produces the tree of the value"() {
        when:
        JsonNode node = mapper.writeValueToTree(new Book("The Stand", 454))

        then:
        node.isObject()
        node.get("title").stringValue == "The Stand"
        node.get("pages").intValue == 454

        when:
        JsonNode typed = mapper.writeValueToTree(Argument.of(Book), new Book("IT", 1138))

        then:
        typed.get("title").stringValue == "IT"

        and:
        mapper.writeValueToTree(null).isNull()
        mapper.writeValueToTree(Argument.of(Book), null).isNull()
    }

    def "readValueFromTree reads the tree"() {
        given:
        def node = JsonNode.createObjectNode([title: JsonNode.createStringNode("Dune"), pages: JsonNode.createNumberNode(412)])

        expect:
        mapper.readValueFromTree(node, Book) == new Book("Dune", 412)
        mapper.readValueFromTree(JsonNode.nullNode(), Argument.of(Book)) == null
    }

    def "updateValue merges yaml into an existing value"() {
        given:
        def book = new MutableBook(title: "Draft", pages: 1)

        when:
        mapper.updateValue(book, Argument.of(MutableBook), "pages: 300\n".bytes)

        then:
        book.title == "Draft"
        book.pages == 300

        when:
        mapper.updateValue(book, Argument.of(MutableBook), new ByteArrayInputStream("title: Final\n".bytes))

        then:
        book.title == "Final"
        book.pages == 300

        when:
        mapper.updateValueFromTree(book, JsonNode.createObjectNode([pages: JsonNode.createNumberNode(5)]))

        then:
        book.pages == 5

        when: "a null document leaves the value alone"
        mapper.updateValue(book, Argument.of(MutableBook), "null\n".bytes)

        then:
        book.title == "Final"
    }

    def "cloneWithViewClass and cloneWithConfiguration return yaml mappers"() {
        when:
        def viewed = mapper.cloneWithViewClass(Object)
        def reconfigured = mapper.cloneWithConfiguration(null, null, null)

        then:
        viewed instanceof YamlObjectMapper
        !viewed.is(mapper)
        reconfigured instanceof YamlObjectMapper
        reconfigured.writeValueAsString(new Book("A", 1)) == "title: A\npages: 1\n"
    }

    def "cloneWithConfiguration applies a different yaml configuration"() {
        given:
        def configuration = new SerdeYamlConfiguration()
        configuration.writeFeatures.writeStyle = SerdeYamlConfiguration.WriteStyle.FLOW

        when:
        def flow = mapper.cloneWithConfiguration(configuration)

        then:
        flow.yamlConfiguration.is(configuration)
        flow.writeValueAsString(new Book("A", 1)) == "{title: A, pages: 1}\n"
        mapper.writeValueAsString(new Book("A", 1)) == "title: A\npages: 1\n"
    }
}
