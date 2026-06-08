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

import io.micronaut.core.type.Argument
import tools.jackson.databind.JavaType
import tools.jackson.dataformat.javaprop.JavaPropsMapper

import java.nio.charset.StandardCharsets

abstract class AbstractJacksonDatabindPropSpec extends AbstractPropSerializationSpec {

    abstract JavaPropsMapper getDatabindPropertiesMapper()

    @Override
    def <T> T readProperties(String properties, Argument<T> type) {
        databindPropertiesMapper.readValue(properties, toJavaType(type))
    }

    @Override
    def <T> T readProperties(byte[] properties, Argument<T> type) {
        databindPropertiesMapper.readValue(properties, toJavaType(type))
    }

    @Override
    def <T> T readProperties(InputStream properties, Argument<T> type) {
        databindPropertiesMapper.readValue(properties, toJavaType(type))
    }

    @Override
    String writeProperties(Object bean) {
        new String(writePropertiesAsBytes(bean), StandardCharsets.UTF_8)
    }

    @Override
    String writeProperties(Argument<?> argument, Object bean) {
        new String(writePropertiesAsBytes(argument, bean), StandardCharsets.UTF_8)
    }

    @Override
    byte[] writePropertiesAsBytes(Object bean) {
        databindPropertiesMapper.writeValueAsBytes(bean)
    }

    @Override
    byte[] writePropertiesAsBytes(Argument<?> argument, Object bean) {
        databindPropertiesMapper.writerFor(toJavaType(argument)).writeValueAsBytes(bean)
    }

    @Override
    void writeProperties(OutputStream outputStream, Argument<?> argument, Object bean) {
        databindPropertiesMapper.writerFor(toJavaType(argument)).writeValue(outputStream, bean)
    }

    private JavaType toJavaType(Argument<?> argument) {
        if (!argument.typeParameters) {
            return databindPropertiesMapper.typeFactory.constructType(argument.type)
        }
        return databindPropertiesMapper.typeFactory.constructParametricType(
                argument.type,
                argument.typeParameters.collect { toJavaType(it) } as JavaType[]
        )
    }
}
