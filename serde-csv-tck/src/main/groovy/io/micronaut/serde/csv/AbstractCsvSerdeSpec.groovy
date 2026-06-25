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

    void "reads CSV without schema as list rows directly"() {
        given:
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        readCsvDirect(csv, target) == [
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

    void "reads CSV without schema and skips blank lines"() {
        given:
        def csv = "\n" +
            "1,2,true\n" +
            "   \n" +
            "2,9,false\n" +
            "\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        readCsv(csv, target) == [
            ["1", "2", "true"],
            ["2", "9", "false"]
        ]
    }

    void "reads CSV without schema and keeps trailing empty cells"() {
        given:
        def csv = "1,2,\n" +
            "3,,false\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        readCsv(csv, target) == [
            ["1", "2", ""],
            ["3", "", "false"]
        ]
    }

    void "reads CSV without schema and keeps leading empty cells"() {
        given:
        def csv = ",2,true\n" +
            ",,false\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        readCsv(csv, target) == [
            ["", "2", "true"],
            ["", "", "false"]
        ]
    }

    void "reads CSV without schema and keeps comma-only rows"() {
        given:
        def csv = "1,red\n" +
            ",,\n" +
            ",,hello\n" +
            "2,blue\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        readCsv(csv, target) == [
            ["1", "red"],
            ["", "", ""],
            ["", "", "hello"],
            ["2", "blue"]
        ]
    }

    void "reads CSV without schema with CRLF line endings"() {
        given:
        def csv = "1,2,true\r\n" +
            "2,9,false\r\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        readCsv(csv, target) == [
            ["1", "2", "true"],
            ["2", "9", "false"]
        ]
    }

    void "reads CSV without schema without trailing newline"() {
        given:
        def csv = "1,2,true\n" +
            "2,9,false"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        readCsv(csv, target) == [
            ["1", "2", "true"],
            ["2", "9", "false"]
        ]
    }

    void "reads CSV without schema with variable length rows"() {
        given:
        def csv = "1,2\n" +
            "1,2,3,4\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        readCsv(csv, target) == [
            ["1", "2"],
            ["1", "2", "3", "4"]
        ]
    }

    void "reads comment-looking rows as data by default"() {
        given:
        def csv = "# comment\n" +
            "a,b\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        readCsv(csv, target) == [
            ["# comment"],
            ["a", "b"]
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

    void "reads CSV with header and keeps empty map values"() {
        given:
        def header = "A,B,C"
        def csv = "1,2,\n" +
            "3,,false\n"
        Argument<List<Map<String, String>>> target = (Argument<List<Map<String, String>>>) Argument.listOf(
            Argument.mapOf(String, String)
        )

        expect:
        readCsvWithHeader(header, csv, target) == [
            [A: "1", B: "2", C: ""],
            [A: "3", B: "", C: "false"]
        ]
    }

    void "reads CSV with header and keeps leading empty map values"() {
        given:
        def header = "A,B,C"
        def csv = ",2,true\n" +
            ",,false\n"
        Argument<List<Map<String, String>>> target = (Argument<List<Map<String, String>>>) Argument.listOf(
            Argument.mapOf(String, String)
        )

        expect:
        readCsvWithHeader(header, csv, target) == [
            [A: "", B: "2", C: "true"],
            [A: "", B: "", C: "false"]
        ]
    }

    void "reads CSV with header and skips blank data lines"() {
        given:
        def header = "A,B,C"
        def csv = "\n" +
            "1,2,true\n" +
            "   \n" +
            "3,,false\n"
        Argument<List<Map<String, String>>> target = (Argument<List<Map<String, String>>>) Argument.listOf(
            Argument.mapOf(String, String)
        )

        expect:
        readCsvWithHeader(header, csv, target) == [
            [A: "1", B: "2", C: "true"],
            [A: "3", B: "", C: "false"]
        ]
    }

    void "reads CSV with header and missing trailing columns"() {
        given:
        def header = "A,B,C"
        def csv = "data11,data12\n" +
            "data21,data22,data23\n"
        Argument<List<Map<String, String>>> target = (Argument<List<Map<String, String>>>) Argument.listOf(
            Argument.mapOf(String, String)
        )

        expect:
        readCsvWithHeader(header, csv, target) == [
            [A: "data11", B: "data12"],
            [A: "data21", B: "data22", C: "data23"]
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

    void "reads CSV with spaced first row schema as points"() {
        given:
        def header = "x, y, visible"
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

    void "reads blog post CSV as map rows with explicit columns directly"() {
        given:
        def header = "x,y,visible"
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<Map<String, String>>> target = (Argument<List<Map<String, String>>>) Argument.listOf(
            Argument.mapOf(String, String)
        )

        expect:
        readCsvWithHeaderDirect(header, csv, target) == [
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

    void "reads blog post CSV as points with first row schema directly"() {
        given:
        def header = "x,y,visible"
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<CsvPoint>> target = (Argument<List<CsvPoint>>) Argument.listOf(CsvPoint)

        when:
        def points = readCsvWithHeaderDirect(header, csv, target)

        then:
        points*.x == ["1", "2", "-13"]
        points*.y == ["2", "9", "0"]
        points*.visible == ["true", "false", "true"]
    }

    void "writes CSV without schema from list rows"() {
        given:
        def rows = [
            ["1", "2", "true"],
            ["2", "9", "false"],
            ["-13", "0", "true"]
        ]
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        writeCsv(target, rows) == "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
    }

    void "writes CSV without schema and escapes cell values"() {
        given:
        def rows = [
            ["a,b", 'a "quote"', "plain"]
        ]
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.listOf(
            Argument.listOf(String)
        )

        expect:
        writeCsv(target, rows) == '"a,b","a ""quote""",plain\n'
    }

    void "writes CSV with first row schema from map rows"() {
        given:
        def rows = [
            [x: "1", y: "2", visible: "true"],
            [x: "2", y: "9", visible: "false"],
            [x: "-13", y: "0", visible: "true"]
        ]
        Argument<List<Map<String, String>>> target = (Argument<List<Map<String, String>>>) Argument.listOf(
            Argument.mapOf(String, String)
        )

        expect:
        writeCsvWithHeader("x,y,visible", target, rows) == "x,y,visible\n" +
            "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
    }

    void "writes CSV with first row schema from explicit header order"() {
        given:
        def rows = [
            [x: "1", y: "2", visible: "true"],
            [x: "2", y: "9", visible: "false"],
            [x: "-13", y: "0", visible: "true"]
        ]
        Argument<List<Map<String, String>>> target = (Argument<List<Map<String, String>>>) Argument.listOf(
            Argument.mapOf(String, String)
        )

        expect:
        writeCsvWithHeader("visible,x,y", target, rows) == "visible,x,y\n" +
            "true,1,2\n" +
            "false,2,9\n" +
            "true,-13,0\n"
    }

    void "writes CSV with first row schema inferred from bean properties"() {
        given:
        def points = [
            point("1", "2", "true"),
            point("2", "9", "false"),
            point("-13", "0", "true")
        ]
        Argument<List<CsvPoint>> target = (Argument<List<CsvPoint>>) Argument.listOf(CsvPoint)

        expect:
        writeCsvWithInferredHeader(target, points) == "x,y,visible\n" +
            "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
    }

    void "writes CSV with first row schema inferred from empty bean rows"() {
        given:
        Argument<List<CsvPoint>> target = (Argument<List<CsvPoint>>) Argument.listOf(CsvPoint)

        expect:
        writeCsvWithInferredHeader(target, []) == "x,y,visible\n"
    }

    private static CsvPoint point(String x, String y, String visible) {
        def point = new CsvPoint()
        point.x = x
        point.y = y
        point.visible = visible
        point
    }
}
