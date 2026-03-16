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
package io.micronaut.serde.processor.sourcegen.enums;

import io.micronaut.core.annotation.Creator;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves enum shapes eligible for source-generated serdes.
 */
public final class EnumSerdeShapeResolver {

    public Optional<EnumSerdeShape> resolve(ClassElement element) {
        if (!element.isEnum()) {
            return Optional.empty();
        }
        if (!element.getTypeArguments().isEmpty()) {
            return Optional.empty();
        }
        if (hasCustomEnumValue(element) || hasCustomCreator(element)) {
            return Optional.empty();
        }

        List<EnumSerdeShape.EnumConstant> constants = new ArrayList<>();
        boolean hasOverrides = false;
        for (FieldElement field : element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared())) {
            if (!field.isStatic()
                || !field.isFinal()
                || field.getName().startsWith("$")
                || field.getType().isPrimitive()
                || field.getType().isArray()) {
                continue;
            }
            String name = field.getName();
            String serializedValue = field.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                .or(() -> field.stringValue("com.fasterxml.jackson.annotation.JsonProperty"))
                .or(() -> field.stringValue("tools.jackson.annotation.JsonProperty"))
                .orElse(name);
            constants.add(new EnumSerdeShape.EnumConstant(name, serializedValue));
            if (!serializedValue.equals(name)) {
                hasOverrides = true;
            }
        }
        return Optional.of(new EnumSerdeShape(List.copyOf(constants), hasOverrides));
    }

    private boolean hasCustomEnumValue(ClassElement element) {
        if (element.getBeanProperties().stream().anyMatch(p -> p.hasDeclaredAnnotation(SerdeConfig.SerValue.class))) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared().annotated(a -> a.hasDeclaredAnnotation(SerdeConfig.SerValue.class))).isEmpty()) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().annotated(a -> a.hasDeclaredAnnotation(SerdeConfig.SerValue.class))).isEmpty();
    }

    private boolean hasCustomCreator(ClassElement element) {
        if (element.getPrimaryConstructor().map(c -> c.hasDeclaredAnnotation(Creator.class)).orElse(false)) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().annotated(a -> a.hasDeclaredAnnotation(Creator.class))).isEmpty();
    }
}
