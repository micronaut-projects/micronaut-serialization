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

import java.io.InputStream
import java.io.OutputStream

interface PropertiesSpec {

    def <T> T readProperties(String properties, Argument<T> type)

    def <T> T readProperties(byte[] properties, Argument<T> type)

    def <T> T readProperties(InputStream properties, Argument<T> type)

    String writeProperties(Object bean)

    String writeProperties(Argument<?> argument, Object bean)

    byte[] writePropertiesAsBytes(Object bean)

    byte[] writePropertiesAsBytes(Argument<?> argument, Object bean)

    void writeProperties(OutputStream outputStream, Argument<?> argument, Object bean)
}
