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
package io.micronaut.serde.yaml

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.AbstractJsonCompileSpec

abstract class AbstractYamlCompileSpec extends AbstractJsonCompileSpec implements YamlSpec {

    ApplicationContext buildContext(String className, String source) {
        buildContext(className, source, [:], [:])
    }

    ApplicationContext buildContext(String className, String source, Map<String, Object> properties,
                                    Map<String, Object> contextProperties) {
        ApplicationContext context = buildContext(source, contextProperties)
        typeUnderTest = argumentOf(context, className)
        beanUnderTest = properties.isEmpty() ? null : newInstance(context, className, properties)
        context
    }

    ApplicationContext buildContext(String source, Map<String, Object> contextProperties = [:]) {
        Map<String, Object> properties = new LinkedHashMap<>(getContextProperties())
        properties.putAll(contextProperties)
        ApplicationContext context = super.buildContext("test.Source" + System.currentTimeMillis(), source, true, properties)
        initializeMapper(context)
        return context
    }

    protected void initializeMapper(ApplicationContext context) {
        jsonMapper = context.getBean(getJsonMapperClass())
    }

    protected Map<String, Object> getContextProperties() {
        [:]
    }
}
