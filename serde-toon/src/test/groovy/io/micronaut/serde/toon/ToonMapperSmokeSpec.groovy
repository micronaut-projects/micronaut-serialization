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
package io.micronaut.serde.toon

import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractJsonCompileSpec

/**
 * Phase 1 scaffolding smoke test: verifies the {@code toon}-qualified
 * {@link ToonMapper} bean is wired through DI and that the stub
 * writer/adapter can round-trip a flat object. This is deliberately
 * minimal - full format coverage lands in later phases.
 */
class ToonMapperSmokeSpec extends AbstractJsonCompileSpec {

    @Override
    Class<JsonMapper> getJsonMapperClass() {
        ToonMapper
    }

    void 'test the toon ObjectMapper bean is wired and round-trips a flat object'() {
        given:
        def context = buildContext('test.Test', '''
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private String value;
    public void setValue(String value) { this.value = value; }
    public String getValue() { return value; }
}
''', [value: 'hello'])

        expect:
        jsonMapper instanceof ToonMapper

        when:
        def bytes = jsonMapper.writeValueAsBytes(beanUnderTest)
        def read = jsonMapper.readValue(bytes, typeUnderTest)

        then:
        new String(bytes) == 'value: hello'
        read.value == 'hello'

        cleanup:
        context.close()
    }
}
