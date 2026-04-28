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
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.EnumConstantElement;
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
        if (hasJsonEnumDefaultValue(element)) {
            return Optional.empty();
        }

        List<EnumSerdeShape.EnumConstant> overrides = new ArrayList<>();
        for (EnumConstantElement enumConstant : ((EnumElement) element).elements()) {
            String name = enumConstant.getName();
            String serializedValue = enumConstant.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                .or(() -> enumConstant.stringValue("com.fasterxml.jackson.annotation.JsonProperty", "value"))
                .orElse(name);
            if (!serializedValue.equals(name)) {
                overrides.add(new EnumSerdeShape.EnumConstant(name, serializedValue));
            }
        }
        return Optional.of(new EnumSerdeShape(List.copyOf(overrides), !overrides.isEmpty()));
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

    private boolean hasJsonEnumDefaultValue(ClassElement element) {
        for (EnumConstantElement enumConstant : ((EnumElement) element).elements()) {
            if (enumConstant.hasAnnotation(SerdeConfig.SerEnumDefaultValue.class)) {
                return true;
            }
        }
        return false;
    }
}
