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
package io.micronaut.serde.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.config.DeserializationConfiguration;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Shared error and enum helper methods used by generated serdes.
 */
@Internal
public final class GeneratedSerdeErrorHandler {

    private GeneratedSerdeErrorHandler() {
    }

    public static SerdeException unknownProperty(String propertyName, Argument<?> beanType) {
        SerdeException serdeException = new SerdeException("Unknown property [" + propertyName + "] encountered during deserialization of type: " + beanType);
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), Argument.OBJECT_ARGUMENT.withName(propertyName)));
        return serdeException;
    }

    public static SerdeException duplicateProperty(String propertyName, Argument<?> beanType) {
        SerdeException serdeException = new SerdeException("Duplicate property [" + propertyName + "] encountered during deserialization of type: " + beanType);
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), Argument.OBJECT_ARGUMENT.withName(propertyName)));
        return serdeException;
    }

    public static SerdeException unknownEnumValue(Argument<?> enumType, String value) {
        Object[] constants = enumType.getType().getEnumConstants();
        String acceptedValues = constants == null ? "[]" : java.util.Arrays.toString(constants);
        return new SerdeException("Cannot deserialize value of type `" + enumType.getType().getName() + "` due to: Expected one of " + acceptedValues + " but was '" + value + "'");
    }

    public static String enumSerializedName(Enum<?> enumValue) {
        try {
            var field = enumValue.getDeclaringClass().getField(enumValue.name());
            for (Annotation annotation : field.getDeclaredAnnotations()) {
                String annotationName = annotation.annotationType().getName();
                if (annotationName.equals("com.fasterxml.jackson.annotation.JsonProperty") || annotationName.equals("tools.jackson.annotation.JsonProperty")) {
                    Method valueMethod = annotation.annotationType().getMethod("value");
                    Object configured = valueMethod.invoke(annotation);
                    if (configured instanceof String configuredValue && !configuredValue.isBlank()) {
                        return configuredValue;
                    }
                }
            }
        } catch (Exception ignore) {
            return enumValue.name();
        }
        return enumValue.name();
    }

    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> E enumValueOf(Class<E> enumType, String serializedValue, Deserializer.DecoderContext context) {
        boolean caseInsensitive = context.getDeserializationConfiguration()
            .map(DeserializationConfiguration::acceptCaseInsensitiveEnums)
            .orElse(false);
        Object[] constants = enumType.getEnumConstants();
        if (constants != null) {
            for (Object constant : constants) {
                E enumConstant = (E) constant;
                String candidate = enumSerializedName(enumConstant);
                if (candidate.equals(serializedValue) || (caseInsensitive && candidate.equalsIgnoreCase(serializedValue))) {
                    return enumConstant;
                }
            }
        }
        return Enum.valueOf(enumType, serializedValue);
    }

    public static void handleUnknownProperty(Decoder decoder,
                                             Deserializer.DecoderContext context,
                                             String propertyName,
                                             Argument<?> beanType) throws IOException {
        boolean ignoreUnknown = context.getDeserializationConfiguration()
            .map(DeserializationConfiguration::isIgnoreUnknown)
            .orElse(true);
        if (ignoreUnknown) {
            decoder.skipValue();
        } else {
            throw unknownProperty(propertyName, beanType);
        }
    }

    public static SerdeException withPropertyPath(SerdeException exception,
                                                  Argument<?> beanType,
                                                  String propertyName,
                                                  Argument<?> propertyArgument) {
        exception.getPath().add(ReferencePath.ofProperty(beanType.getType(), propertyArgument.withName(propertyName)));
        return exception;
    }

    public static SerdeException withPropertyPath(Throwable exception,
                                                  Argument<?> beanType,
                                                  String propertyName,
                                                  Argument<?> propertyArgument) {
        SerdeException serdeException = exception instanceof SerdeException existing
            ? existing
            : new SerdeException("Error processing property [" + propertyName + "] of type [" + beanType + "]: " + exception.getMessage(), exception);
        serdeException.getPath().add(ReferencePath.ofProperty(beanType.getType(), propertyArgument.withName(propertyName)));
        return serdeException;
    }
}
