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

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.DefaultMutableConversionService
import io.micronaut.core.convert.format.Format
import io.micronaut.core.convert.value.ConvertibleMultiValues
import io.micronaut.core.convert.value.MutableConvertibleMultiValuesMap
import io.micronaut.core.type.Argument
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import spock.lang.Specification

class CsvConverterSpec extends Specification {

    void "test convert CSV to List<List<String>> using conversion service"() {
        given:
        def conversionService = new DefaultMutableConversionService()
        conversionService.addConverter(ConvertibleMultiValues, List, new CsvConverter())

        def metadata = new MutableAnnotationMetadata()
        metadata.addAnnotation(
            Format.class.name,
            [(AnnotationMetadata.VALUE_MEMBER): "CSV"]
        )

        def values = new MutableConvertibleMultiValuesMap<String>()
        values.add("p", "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n")

        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.of(
            List,
            "p",
            metadata,
            Argument.listOf(String)
        )

        when:
        def converted = conversionService.convert(values, ConversionContext.of(target))

        then:
        converted.isPresent()
        converted.get() == [
            ["1", "2", "true"],
            ["2", "9", "false"],
            ["-13", "0", "true"]
        ]
    }

    void "test read CSV to List<List<String>> using CSV mapper"() {
        given:
        def mapper = new CsvMapper(null, null)
        def csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
        Argument<List<List<String>>> target = (Argument<List<List<String>>>) Argument.of(
            List,
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
    }
}
