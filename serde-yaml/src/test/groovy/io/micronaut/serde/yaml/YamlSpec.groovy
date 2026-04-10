package io.micronaut.serde.yaml

import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode

import java.nio.charset.StandardCharsets

trait YamlSpec {

    abstract YamlObjectMapper getYamlMapper()


    String writeYaml(Object bean) {
        new String(getYamlMapper().writeValueAsBytes(bean), StandardCharsets.UTF_8)
    }

    String writeYaml(Argument argument, Object bean) {
        new String(getYamlMapper().writeValueAsBytes(argument, bean), StandardCharsets.UTF_8)
    }

    /**
     * Converting JSON string to Yaml with Jackson's JsonNode.
     */
    String YamlString(String json) {
        JsonNode tree = JACKSON_JSON.readTree(json)
        return JACKSON_Yaml.writeValueAsString(tree)
    }

    byte[] YamlBytes(String Yaml) {
        return Yaml.getBytes(StandardCharsets.UTF_8)
    }

    boolean YamlMatches(String result, String expected) {
        result == expected
    }

    boolean objRepresentationMatches(Object obj, String Yaml2) {
        def Yaml1 = YamlMapper.writeValueAsBytes(obj)
        def Yaml1_string = new String(Yaml1, StandardCharsets.UTF_8)
        assert Yaml1_string == Yaml2
        Yaml1_string == Yaml2
    }

    def <T> T serializeDeserialize(T obj) {
        return serializeDeserializeAs(obj, Argument.of(obj.getClass()))
    }

    def <P> P serializeDeserializeAs(P obj, Argument type) {
        def output = getYamlMapper().writeValueAsBytes(obj)
        return getYamlMapper().readValue(output, type) as P
    }
}
