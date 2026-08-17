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
package io.micronaut.serde.processor.sourcegen.records;

import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves record shapes eligible for source-generated serdes.
 */
public final class RecordSerdeShapeResolver {

    public Optional<RecordSerdeShape> resolve(ClassElement element) {
        if (!element.isRecord()) {
            return Optional.empty();
        }
        if (!element.getTypeArguments().isEmpty()) {
            return Optional.empty();
        }
        MethodElement canonicalConstructor = element.getPrimaryConstructor().orElse(null);
        if (canonicalConstructor == null) {
            return Optional.empty();
        }
        Map<String, PropertyElement> propertiesByName = element.getBeanProperties().stream()
            .collect(Collectors.toMap(PropertyElement::getName, Function.identity()));
        List<RecordSerdeShape.RecordComponent> components = new ArrayList<>();
        for (ParameterElement parameter : canonicalConstructor.getParameters()) {
            if (parameter.getType().isTypeVariable()) {
                return Optional.empty();
            }
            PropertyElement propertyElement = propertiesByName.get(parameter.getName());
            if (propertyElement == null) {
                return Optional.empty();
            }
            components.add(new RecordSerdeShape.RecordComponent(
                parameter.getName(),
                resolveName(element, propertyElement, SerdeConfig.SERIALIZE_PROPERTY_NAME, SerdeConfig.SERIALIZE_NAMING),
                resolveName(element, propertyElement, SerdeConfig.DESERIALIZE_PROPERTY_NAME, SerdeConfig.DESERIALIZE_NAMING),
                parameter.getType(),
                propertyElement
            ));
        }
        return Optional.of(new RecordSerdeShape(canonicalConstructor, List.copyOf(components)));
    }

    private static String resolveName(ClassElement element, PropertyElement property, String propertyMember, String namingMember) {
        Optional<String> propertyValue = property.stringValue(SerdeConfig.class, propertyMember);
        if (propertyValue.isPresent()) {
            return propertyValue.get();
        }

        Optional<String> namingValue = element.stringValue(SerdeConfig.class, namingMember);
        if (namingValue.isPresent()) {
            String name = namingValue.get();
            PropertyNamingStrategy strategy = null;

            Optional<PropertyNamingStrategy> strategyByName = PropertyNamingStrategy.forName(name);
            if (strategyByName.isPresent()) {
                strategy = strategyByName.get();
            } else {
                ClassLoader classLoader = RecordSerdeShapeResolver.class.getClassLoader();
                Optional<?> instance = InstantiationUtils.tryInstantiate(name, classLoader);

                if (instance.isPresent() && instance.get() instanceof PropertyNamingStrategy) {
                    strategy = (PropertyNamingStrategy) instance.get();
                }
            }

            if (strategy != null) {
                return strategy.translate(property);
            }
        }

        return property.getName();
    }
}
