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

import io.micronaut.core.type.Argument
import io.micronaut.serde.csv.fixture.CsvBook
import io.micronaut.serde.csv.fixture.CsvPoint
import spock.lang.Specification

import java.nio.charset.StandardCharsets

abstract class AbstractCsvSerdeSpec extends Specification implements CsvSpec {

    void "reads CSV without schema as list rows"() {
        given:
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        readCsv(csv, target) == [
            ["1", "2", "true"],
            ["2", "9", "false"],
            ["-13", "0", "true"]
        ]
    }

    void "reads CSV bytes without schema as list rows"() {
        given:
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        readCsv(csv.getBytes(StandardCharsets.UTF_8), target) == [
            ["1", "2", "true"],
            ["2", "9", "false"],
            ["-13", "0", "true"]
        ]
    }

    void "reads CSV stream without schema as list rows"() {
        given:
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        readCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), target) == [
            ["1", "2", "true"],
            ["2", "9", "false"],
            ["-13", "0", "true"]
        ]
    }

    void "reads CSV with first row schema as map rows"() {
        given:
        def header = "A,B,C"
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<Map<String, String>>> target = (Argument<List<Map<String, String>>>) Argument.listOf(
            Argument.mapOf(String, String)
        )

        expect:
        readCsvWithHeader(header, csv, target) == [
            [A: "1", B: "2", C: "true"],
            [A: "2", B: "9", C: "false"],
            [A: "-13", B: "0", C: "true"]
        ]
    }

    void "reads CSV with first row schema as beans"() {
        given:
        def header = "title,pages,available"
        def csv = "Micronaut in Action,320,true\n" +
            "CSV in Action,42,false\n"
        Argument<List<CsvBook>> target = (Argument<List<CsvBook>>) Argument.listOf(CsvBook)

        when:
        def books = readCsvWithHeader(header, csv, target)

        then:
        books*.title == ["Micronaut in Action", "CSV in Action"]
        books*.pages == ["320", "42"]
        books*.available == ["true", "false"]
    }

    void "reads blog post CSV as map rows with explicit columns"() {
        given:
        def header = "x,y,visible"
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<Map<String, String>>> target = (Argument<List<Map<String, String>>>) Argument.listOf(
            Argument.mapOf(String, String)
        )

        expect:
        readCsvWithHeader(header, csv, target) == [
            [x: "1", y: "2", visible: "true"],
            [x: "2", y: "9", visible: "false"],
            [x: "-13", y: "0", visible: "true"]
        ]
    }

    void "reads blog post CSV as points with first row schema"() {
        given:
        def header = "x,y,visible"
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<CsvPoint>> target = (Argument<List<CsvPoint>>) Argument.listOf(CsvPoint)

        when:
        def points = readCsvWithHeader(header, csv, target)

        then:
        points*.x == ["1", "2", "-13"]
        points*.y == ["2", "9", "0"]
        points*.visible == ["true", "false", "true"]
    }
}
