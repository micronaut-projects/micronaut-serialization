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
package io.micronaut.serde.properties

import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import io.micronaut.serde.AbstractJsonCompileSpec

import java.nio.charset.StandardCharsets

/**
 * Base compile spec wiring the tests to the {@link PropertiesMapper} bean. DTOs are compiled at
 * test time (no pre-written Java fixtures) following the project convention.
 */
abstract class PropertiesCompileSpec extends AbstractJsonCompileSpec {

    @Override
    Class<JsonMapper> getJsonMapperClass() {
        PropertiesMapper
    }

    ApplicationContext buildContext(String source, Map<String, Object> contextProperties) {
        ApplicationContext context = super.buildContext("test.Source" + System.currentTimeMillis(), source, true, contextProperties)
        jsonMapper = context.getBean(getJsonMapperClass())
        return context
    }

    protected <T> T readProperties(String properties, Object type) {
        jsonMapper.readValue(properties.getBytes(StandardCharsets.UTF_8), type)
    }

    protected String writeProperties(Object bean) {
        new String(jsonMapper.writeValueAsBytes(bean), StandardCharsets.UTF_8)
    }
}
