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

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.util.SerdePropertyAccess;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the methods the runtime object serializer writes besides the bean properties.
 *
 * <p>A method annotated with {@code @JsonGetter}, or a method that is not a property accessor and is
 * annotated with {@code @JsonProperty}, is a {@link SerdeConfig.SerGetter} bean method. The runtime
 * serializer writes such a method after the bean properties, under its declared name or the name derived
 * from the method name, unless a property is already written under that name.</p>
 *
 * @since 3.2
 */
@Internal
public final class SerdeSourceGenGetterMethods {

    private static final String JACKSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";

    private SerdeSourceGenGetterMethods() {
    }

    /**
     * The getter methods of the type in the order the introspection lists its bean methods: the
     * executable property accessors first, followed by the other executable methods.
     *
     * @param element    The type
     * @param properties The introspected properties of the type
     * @return The getter methods, including the ones the runtime serializer does not write
     */
    public static List<MethodElement> getterMethods(ClassElement element, List<PropertyElement> properties) {
        Set<MethodElement> beanMethods = new LinkedHashSet<>();
        for (PropertyElement property : properties) {
            if (property.isExcluded()) {
                continue;
            }
            property.getReadMethod().filter(SerdeSourceGenGetterMethods::isBeanMethod).ifPresent(beanMethods::add);
            property.getWriteMethod().filter(SerdeSourceGenGetterMethods::isBeanMethod).ifPresent(beanMethods::add);
        }
        beanMethods.addAll(element.getEnclosedElements(ElementQuery.ALL_METHODS
            .onlyInstance()
            .annotated(annotationMetadata -> annotationMetadata.hasStereotype(Executable.class))));
        List<MethodElement> getterMethods = new ArrayList<>(beanMethods.size());
        for (MethodElement method : beanMethods) {
            if (method.hasAnnotation(SerdeConfig.SerGetter.class)) {
                getterMethods.add(method);
            }
        }
        return getterMethods;
    }

    /**
     * Resolves the getter methods the runtime serializer writes after the given properties.
     *
     * @param element                 The type
     * @param properties              The introspected properties of the type
     * @param serializedPropertyNames The names the serialized properties are written under
     * @return The written getter methods, in write order
     */
    public static List<GetterMethod> resolve(ClassElement element,
                                             List<PropertyElement> properties,
                                             Collection<String> serializedPropertyNames) {
        List<MethodElement> methods = getterMethods(element, properties);
        if (methods.isEmpty()) {
            return List.of();
        }
        Set<String> writtenNames = CollectionUtils.newHashSet(serializedPropertyNames.size() + methods.size());
        writtenNames.addAll(serializedPropertyNames);
        List<GetterMethod> getterMethods = new ArrayList<>(methods.size());
        for (MethodElement method : methods) {
            AnnotationMetadata annotationMetadata = method.getAnnotationMetadata();
            if (!isSerialized(annotationMetadata)) {
                continue;
            }
            String originalName = NameUtils.getPropertyNameForGetter(method.getName());
            String serializedName = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                .or(() -> annotationMetadata.stringValue(JACKSON_PROPERTY))
                .orElse(originalName);
            if (!writtenNames.add(serializedName)) {
                continue;
            }
            Map<String, String> keyMetadata = resolveKeyMetadata(annotationMetadata);
            getterMethods.add(new GetterMethod(
                originalName,
                serializedName,
                method,
                method.getGenericReturnType(),
                keyMetadata,
                SerdeInclusionSourceGen.resolveMethodInclude(element, method, keyMetadata)
            ));
        }
        return getterMethods;
    }

    /**
     * Whether the runtime object serializer writes a getter method.
     *
     * @param annotationMetadata The metadata of the method
     * @return {@code true} if the method is serialized
     */
    public static boolean isSerialized(AnnotationMetadata annotationMetadata) {
        return !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false)
            && !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_SERIALIZATION).orElse(false)
            && SerdePropertyAccess.canSerialize(annotationMetadata);
    }

    private static boolean isBeanMethod(MethodElement method) {
        return method.hasStereotype(Executable.class) && !method.isAbstract();
    }

    private static Map<String, String> resolveKeyMetadata(AnnotationMetadata annotationMetadata) {
        Map<String, String> metadata = CollectionUtils.newHashMap(10);
        for (String member : List.of(
            SerdeConfig.XML_ATTRIBUTE_PROPERTY,
            SerdeConfig.XML_TEXT_PROPERTY,
            SerdeConfig.XML_CDATA_PROPERTY,
            SerdeConfig.XML_LIST_PROPERTY,
            SerdeConfig.XML_MIXED_PROPERTY)) {
            annotationMetadata.booleanValue(SerdeConfig.class, member)
                .filter(Boolean::booleanValue)
                .ifPresent(value -> metadata.put(member, "true"));
        }
        for (String member : List.of(SerdeConfig.META_ANNOTATION_PROPERTY, SerdeConfig.XML_NILLABLE, SerdeConfig.XML_WRAPPER_NILLABLE)) {
            annotationMetadata.booleanValue(SerdeConfig.class, member)
                .ifPresent(value -> metadata.put(member, value.toString()));
        }
        for (String member : List.of(
            SerdeConfig.XML_NAMESPACE,
            SerdeConfig.WRAPPER_PROPERTY,
            SerdeConfig.XML_WRAPPER_NAMESPACE,
            SerdeConfig.XML_DEFAULT_VALUE)) {
            annotationMetadata.stringValue(SerdeConfig.class, member)
                .ifPresent(value -> metadata.put(member, value));
        }
        return Map.copyOf(metadata);
    }

    /**
     * A getter method the generated serializer writes.
     *
     * @param originalName   The name derived from the method name, which a property order can refer to
     * @param serializedName The name the value is written under
     * @param method         The method returning the value
     * @param type           The return type of the method
     * @param keyMetadata    Pre-resolved metadata contributed with the key
     * @param include        The inclusion resolved at build time, or {@code null} when the configuration decides
     */
    public record GetterMethod(
        String originalName,
        String serializedName,
        MethodElement method,
        ClassElement type,
        Map<String, String> keyMetadata,
        SerdeConfig.@Nullable SerInclude include
    ) {
    }
}
