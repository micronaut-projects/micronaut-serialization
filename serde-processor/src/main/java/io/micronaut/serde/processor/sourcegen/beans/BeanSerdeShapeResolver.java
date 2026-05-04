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
package io.micronaut.serde.processor.sourcegen.beans;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves bean shapes eligible for source-generated serdes.
 */
public final class BeanSerdeShapeResolver {

    public Optional<BeanSerdeShape> resolve(ClassElement element) {
        if (element.isInterface() || element.isEnum() || element.isRecord()) {
            return Optional.empty();
        }
        if (!element.getTypeArguments().isEmpty()) {
            return Optional.empty();
        }
        MethodElement defaultConstructor = element.getAccessibleConstructors().stream()
            .filter(c -> c.getParameters().length == 0)
            .findFirst()
            .orElse(null);
        if (defaultConstructor == null) {
            return Optional.empty();
        }

        List<PropertyElement> beanProperties = element.getBeanProperties();
        if (beanProperties.isEmpty()) {
            return Optional.empty();
        }
        List<BeanSerdeShape.BeanProperty> properties = new ArrayList<>(beanProperties.size());
        for (PropertyElement property : beanProperties) {
            MethodElement readMethod = null;
            MethodElement writeMethod = null;
            FieldElement readField = null;
            FieldElement writeField = null;
            if (property.getReadAccessKind() == PropertyElement.AccessKind.FIELD) {
                readField = property.getField()
                    .filter(field -> field.isAccessible(element, false))
                    .orElse(null);
            } else {
                readMethod = property.getReadMethod().orElse(null);
            }
            if (property.getWriteAccessKind() == PropertyElement.AccessKind.FIELD) {
                writeField = property.getField()
                    .filter(field -> !field.isFinal())
                    .filter(field -> field.isAccessible(element, false))
                    .orElse(null);
            } else {
                writeMethod = property.getWriteMethod().orElse(null);
            }
            if ((readMethod == null && readField == null) || (writeMethod == null && writeField == null)) {
                return Optional.empty();
            }
            ClassElement serializationType = property.getReadType().orElse(null);
            ClassElement deserializationType = property.getWriteType().orElse(null);
            if (serializationType == null || deserializationType == null) {
                return Optional.empty();
            }
            if (serializationType.isTypeVariable() || deserializationType.isTypeVariable()) {
                return Optional.empty();
            }
            properties.add(new BeanSerdeShape.BeanProperty(
                property.getName(),
                serializationType,
                deserializationType,
                property.isNullable(),
                readMethod,
                writeMethod,
                readField,
                writeField
            ));
        }
        return Optional.of(new BeanSerdeShape(defaultConstructor, List.copyOf(properties)));
    }
}
