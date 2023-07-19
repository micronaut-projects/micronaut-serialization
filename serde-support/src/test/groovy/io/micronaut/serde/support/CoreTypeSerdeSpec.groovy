package io.micronaut.serde.support

import io.micronaut.core.type.Argument
import io.micronaut.health.HealthStatus
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.micronaut.json.tree.JsonNode
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.IntStream

@MicronautTest
class CoreTypeSerdeSpec extends Specification {
    @Inject ObjectMapper jsonMapper

    void "test read / write health result"() {
        given:
        HealthResult hr = HealthResult.builder("db", HealthStatus.DOWN)
            .details(Collections.singletonMap("foo", "bar"))
            .build()

        when:
        def result = writeJson(hr)

        then:
        result == '{"name":"db","status":"DOWN","details":{"foo":"bar"}}'

        when:
        hr = jsonMapper.readValue(result, Argument.of(HealthResult))

        then:
        hr.name == 'db'
        hr.status == HealthStatus.DOWN
    }

    void "test read / write JsonError"() {
        given:
        JsonError error = new JsonError("my error")
        error.link(Link.SELF, "http://test")
        error.embedded("info", new MyInfo("Additional Info").link(Link.SELF, "http://info.com"))

        when:
        def result = writeJson(error)
        then:
        result == '{"_links":{"self":[{"href":"http://test","templated":false}]},"_embedded":{"info":[{"_links":{"self":[{"href":"http://info.com","templated":false}]},"info":"Additional Info"}]},"message":"my error"}'

        when:
        def read = jsonMapper.readValue(result, Argument.of(JsonError))

        then:
        read != null
        read.message == 'my error'
//      TODO: fix me?
//        read.links.size() == 1
//        read.embedded.size() == 1
    }

    private String writeJson(Object o) {
        new String(jsonMapper.writeValueAsBytes(o), StandardCharsets.UTF_8)
    }

    void "test read / write JsonNode"() {
        given:
        JsonNode node = JsonNode.createObjectNode([
                'array': JsonNode.createArrayNode([
                        JsonNode.createBooleanNode(true)
                ]),
                'null': JsonNode.nullNode(),
                'int': JsonNode.createNumberNode(Integer.MAX_VALUE),
                'double': JsonNode.createNumberNode(Double.MAX_VALUE),
                'long': JsonNode.createNumberNode(Long.MAX_VALUE),
                //'float': JsonNode.createNumberNode(Float.MAX_VALUE), deserialized as double by default
                //'bd': JsonNode.createNumberNode(BigDecimal.valueOf(Double.MAX_VALUE).add(BigDecimal.valueOf(0.1))), deserialized as double by default
                'bi': JsonNode.createNumberNode(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)),
                'str': JsonNode.createStringNode('foo')
        ])

        when:
        def result = writeJson(node)
        def read = jsonMapper.readValue(result, Argument.of(JsonNode))

        then:
        read == node
    }

    void "test read concurrency"() {
        given:
        def results = new ConcurrentHashMap<Integer, SimpleInfo>()

        when:
        ExecutorService threadPool =  Executors.newFixedThreadPool(1000)
        IntStream.range(0, 1000).forEach(i -> threadPool.execute(new Runnable() {
            @Override
            void run() {
                results.put(i, jsonMapper.readValue('{"info":"test' + i + '"}', Argument.of(SimpleInfo)))
            }
        }));
        threadPool.shutdown()
        try {
            threadPool.awaitTermination(1, TimeUnit.MINUTES)
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting", e)
        }

        then:
        results.size() == 1000
        results.each { assert it.value.getInfo() == "test" + it.key }
    }

    void "test byte array deserialization shapes"(byte[] expected, String json) {
        when:
        def deser = jsonMapper.readValue(json, Argument.of(byte[]))
        then:
        deser == expected

        where:
        expected | json
        [0, 1]   | '[0,1]'
        [0, 1]   | '"' + Base64.encoder.encodeToString([0, 1] as byte[]) + '"'
    }
}
