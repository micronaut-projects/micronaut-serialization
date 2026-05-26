package io.micronaut.serde.toml

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.toml.fixture.ComplexField
import io.micronaut.serde.toml.fixture.ObjectField
import spock.lang.Specification

class TomlFeatureToggleSpec extends Specification {

    void "default toml mapper keeps jackson compatible temporal and null defaults"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def bean = new ComplexField()
        def tree = JsonNode.createObjectNode([
                foo: JsonNode.createStringNode('2021-03-26')
        ])

        expect:
        mapper.readValue('foo = 2021-03-26\n', Argument.of(ObjectField)).foo == '2021-03-26'
        mapper.readValue('foo = "2021-03-26"\n', Argument.of(ObjectField)).foo == '2021-03-26'
        mapper.readValue('foo = 2021-03-26\n'.bytes, Argument.of(ObjectField)).foo == '2021-03-26'
        mapper.readValueFromTree(tree, Argument.of(ObjectField)).foo == '2021-03-26'
        mapper.writeValueAsString(bean) == ""

        cleanup:
        ctx.close()
    }

    void "generic temporal strings are not coerced"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def toml = "foo = ${literal}\n"
        def tree = JsonNode.createObjectNode([
                foo: JsonNode.createStringNode(treeValue)
        ])

        expect:
        mapper.readValue(toml, Argument.of(ObjectField)).foo == expected
        mapper.readValue(toml.bytes, Argument.of(ObjectField)).foo == expected
        mapper.readValueFromTree(tree, Argument.of(ObjectField)).foo == expected

        cleanup:
        ctx.close()

        where:
        literal                                 | treeValue                             | expected
        '2021-03-26'                            | '2021-03-26'                          | '2021-03-26'
        '18:40:15.123456789'                    | '18:40:15.123456789'                  | '18:40:15.123456789'
        '2021-03-26T18:40:15.123456789'         | '2021-03-26T18:40:15.123456789'       | '2021-03-26T18:40:15.123456789'
        '2021-03-26T18:40:15.123456789+01:00'   | '2021-03-26T18:40:15.123456789+01:00' | '2021-03-26T18:40:15.123456789+01:00'
    }

    private static ObjectMapper tomlMapper(ApplicationContext ctx) {
        ctx.getBean(ObjectMapper, Qualifiers.byName("toml"))
    }
}
