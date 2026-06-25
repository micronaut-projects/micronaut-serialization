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
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.AutoCleanup

@MicronautTest(startApplication = false, propertySources = "classpath:csv-tck.properties")
class CsvMapperTckSpec extends AbstractMicronautCsvSpec {

    @Inject
    @Named(CsvMapper.NAME)
    ObjectMapper csvMapper

    @AutoCleanup
    ApplicationContext writeHeaderContext = ApplicationContext.run([
        "micronaut.serde.csv.write-features.header": "FIRST_ROW"
    ])

    @Override
    ObjectMapper getCsvMapperWithWriteHeader() {
        writeHeaderContext.getBean(CsvMapper)
    }

    @Override
    def <T> String writeCsvWithHeader(String header, Argument<T> type, T value) {
        if (header == null) {
            return super.writeCsvWithHeader(header, type, value)
        }
        def configuration = new SerdeCsvConfiguration()
        configuration.writeFeatures.header = SerdeCsvConfiguration.Header.FIRST_ROW
        CsvConverter.write(
            csvMapper.writeValueToTree(type, value),
            configuration,
            header.split(",", -1).collect { it.trim() }
        )
    }

    void "writes list rows when first row schema writing is configured"() {
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
        writeCsvWithInferredHeader(target, rows) == "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n"
    }
}
