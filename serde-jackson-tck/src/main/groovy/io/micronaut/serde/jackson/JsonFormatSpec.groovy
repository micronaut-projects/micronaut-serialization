/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.serde.jackson

import io.micronaut.serde.config.SerdeConfiguration.NumericTimeUnit
import spock.lang.Unroll

import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Year
import java.time.ZoneId
import java.time.ZonedDateTime

abstract class JsonFormatSpec extends JsonCompileSpec {

    @Unroll
    void "test deserialize json number format for date #type"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;

@Serdeable
class Test {
    @JsonFormat(${settings.collect { "$it.key=\"$it.value\"" }.join(",")})
    private $type.name value;
    public void setValue($type.name value) {
        this.value = value;
    }
    public $type.name getValue() {
        return value;
    }
}
""", [:], ['micronaut.serde.numeric-time-unit': timeUnit])

        def jsonString = """
{
    "value": ${value}
}
"""
        def read = jsonMapper.readValue(jsonString, typeUnderTest)

        expect:
        resolver(read.value) == expected

        cleanup:
        context.close()

        where:
        type           | timeUnit                     | value                         | settings                                                 | resolver                                                                 | expected
        Instant        | NumericTimeUnit.SECONDS      | "1640995200"                  | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { Instant i -> i.getEpochSecond() }                                      | 1640995200
        Date           | NumericTimeUnit.MILLISECONDS | "1640995200000"               | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { Date d -> d.getTime() }                                                | 1640995200000
        Timestamp      | NumericTimeUnit.MILLISECONDS | "1640995200000"               | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { Timestamp t -> t.getTime()}                                            | 1640995200000
        LocalDate      | NumericTimeUnit.SECONDS      | "19974"                       | [pattern: "yyyy-MM-dd", timezone: "UTC"]                 | { LocalDate d -> d.toString() }                                          | "2024-09-08"
        LocalDateTime  | NumericTimeUnit.SECONDS      | "\"2024-10-18T23:06:24.722\"" | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSS", timezone: "UTC"]  | { LocalDateTime t -> t.atZone(ZoneId.of("UTC")).toInstant().toString() } | "2024-10-18T23:06:24.722Z"
        ZonedDateTime  | NumericTimeUnit.SECONDS      | "1640995200"                  | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { ZonedDateTime t -> t.toString() }                                      | "2022-01-01T00:00Z"
        OffsetDateTime | NumericTimeUnit.SECONDS      | "1640995200"                  | [pattern: "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone: "UTC"] | { OffsetDateTime t -> t.toString() }                                     | "2022-01-01T00:00Z"
        Year           | NumericTimeUnit.SECONDS      | "2024"                        | [pattern: "yyyy", timezone: "UTC"]                       | { Year y -> y.toString() }                                               | "2024"
    }

}
