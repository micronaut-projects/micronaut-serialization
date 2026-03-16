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
package io.micronaut.serde.processor.sourcegen;

import io.micronaut.inject.ast.ClassElement;

/**
 * Centralized naming rules for generated serializer and deserializer classes.
 */
public final class SerdeSourceGenClassNaming {

    private static final String PREFIX = "Serde";
    private static final String SERIALIZER_SUFFIX = "Serializer";
    private static final String DESERIALIZER_SUFFIX = "Deserializer";

    private SerdeSourceGenClassNaming() {
    }

    public static String generatedSerializerClassName(ClassElement element) {
        return generatedClassName(element, SERIALIZER_SUFFIX);
    }

    public static String generatedDeserializerClassName(ClassElement element) {
        return generatedClassName(element, DESERIALIZER_SUFFIX);
    }

    private static String generatedClassName(ClassElement element, String suffix) {
        String packageName = element.getPackageName();
        String packagePrefix = packageName.isEmpty() ? "" : packageName + ".";
        String localName = element.getName();
        if (!packageName.isEmpty() && localName.startsWith(packageName + ".")) {
            localName = localName.substring(packageName.length() + 1);
        }
        localName = localName.replace('.', '_').replace('$', '_');
        return packagePrefix + PREFIX + localName + suffix;
    }
}
