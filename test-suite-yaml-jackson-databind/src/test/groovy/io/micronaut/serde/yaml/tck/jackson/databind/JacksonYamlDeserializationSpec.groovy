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
package io.micronaut.serde.yaml.tck.jackson.databind

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.yaml.tck.AbstractYamlDeserializationSpec

class JacksonYamlDeserializationSpec extends AbstractYamlDeserializationSpec implements JacksonDatabindYamlSpec {

    /**
     * Jackson unwraps a root name with a configured reader. Micronaut Serialization has no root
     * name wrapping, so the shared specification reads the wrapped document as a mapping.
     */
    @Override
    protected <T> T readYamlWithRootWrapper(String yaml, Argument<T> type) {
        databindYamlMapper
                .readerFor(JacksonYamlMappers.toJavaType(databindYamlMapper, type))
                .withRootName(type.type.simpleName)
                .readValue(yaml)
    }

    /**
     * The compiled beans are read with a Jackson mapper, so no mapper bean is resolved from the
     * context the specification builds.
     */
    @Override
    protected void initializeMapper(ApplicationContext context) {
    }
}
