/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.csv

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import spock.lang.Specification

class CsvConverterSpec extends Specification {

    void "test parse CSV without schema to indexed object rows"() {
        given:
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"

        expect:
        CsvConverter.parseNoSchema(csv) == JsonNode.createArrayNode([
            JsonNode.createObjectNode([
                "0": JsonNode.createStringNode("1"),
                "1": JsonNode.createStringNode("2"),
                "2": JsonNode.createStringNode("true")
            ]),
            JsonNode.createObjectNode([
                "0": JsonNode.createStringNode("2"),
                "1": JsonNode.createStringNode("9"),
                "2": JsonNode.createStringNode("false")
            ]),
            JsonNode.createObjectNode([
                "0": JsonNode.createStringNode("-13"),
                "1": JsonNode.createStringNode("0"),
                "2": JsonNode.createStringNode("true")
            ])
        ])
    }

    void "test read CSV without schema to List<List<String>> using CSV mapper"() {
        given:
        def context = ApplicationContext.run()
        def mapper = context.getBean(CsvMapper)
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        when:
        def result = mapper.readValue(csv.bytes, target)

        then:
        result == [
            ["1", "2", "true"],
            ["2", "9", "false"],
            ["-13", "0", "true"]
        ]

        cleanup:
        context?.close()
    }

    void "test read CSV without schema to List<Map<String, String>> using CSV mapper"() {
        given:
        def context = ApplicationContext.run()
        def mapper = context.getBean(CsvMapper)
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<Map<String, String>>> target = (Argument<List<Map<String, String>>>) Argument.listOf(
            Argument.mapOf(String, String)
        )

        when:
        def result = mapper.readValue(csv.bytes, target)

        then:
        result == [
            ["0": "1", "1": "2", "2": "true"],
            ["0": "2", "1": "9", "2": "false"],
            ["0": "-13", "1": "0", "2": "true"]
        ]

        cleanup:
        context?.close()
    }

    void "test read CSV with first row schema to List<Map<String, String>> using CSV mapper"() {
        given:
        def context = ApplicationContext.run([
            "micronaut.serde.format.csv.read-features.header": "FIRST_ROW"
        ])
        def mapper = context.getBean(CsvMapper)
        def csv = "A,B,C\n" +
            "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<Map<String, String>>> target = (Argument<List<Map<String, String>>>) Argument.listOf(
            Argument.mapOf(String, String)
        )

        when:
        def result = mapper.readValue(csv.bytes, target)

        then:
        result == [
            [A: "1", B: "2", C: "true"],
            [A: "2", B: "9", C: "false"],
            [A: "-13", B: "0", C: "true"]
        ]

        cleanup:
        context?.close()
    }

    void "test read CSV without schema to List<Map<String, JsonNode>> using CSV mapper"() {
        given:
        def context = ApplicationContext.run()
        def mapper = context.getBean(CsvMapper)
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<Map<String, JsonNode>>> target = (Argument<List<Map<String, JsonNode>>>) Argument.listOf(
            Argument.mapOf(String, JsonNode)
        )

        when:
        def result = mapper.readValue(csv.bytes, target)

        then:
        result == [
            ["0": JsonNode.createStringNode("1"), "1": JsonNode.createStringNode("2"), "2": JsonNode.createStringNode("true")],
            ["0": JsonNode.createStringNode("2"), "1": JsonNode.createStringNode("9"), "2": JsonNode.createStringNode("false")],
            ["0": JsonNode.createStringNode("-13"), "1": JsonNode.createStringNode("0"), "2": JsonNode.createStringNode("true")]
        ]

        cleanup:
        context?.close()
    }
}
