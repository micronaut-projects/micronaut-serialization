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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.annotation.SerdeableGenerated;
import io.micronaut.serde.processor.sourcegen.beans.BeanDeserializerSourceGen;
import io.micronaut.serde.processor.sourcegen.beans.BeanSerdeShape;
import io.micronaut.serde.processor.sourcegen.beans.BeanSerdeShapeResolver;
import io.micronaut.serde.processor.sourcegen.beans.BeanSerializerSourceGen;
import io.micronaut.serde.processor.sourcegen.enums.EnumDeserializerSourceGen;
import io.micronaut.serde.processor.sourcegen.enums.EnumSerdeShape;
import io.micronaut.serde.processor.sourcegen.enums.EnumSerdeShapeResolver;
import io.micronaut.serde.processor.sourcegen.enums.EnumSerializerSourceGen;
import io.micronaut.serde.processor.sourcegen.records.RecordDeserializerSourceGen;
import io.micronaut.serde.processor.sourcegen.records.RecordSerdeShape;
import io.micronaut.serde.processor.sourcegen.records.RecordSerdeShapeResolver;
import io.micronaut.serde.processor.sourcegen.records.RecordSerializerSourceGen;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entry-point visitor that emits simple-shape generated serializers and deserializers.
 */
public final class SerdeSourceGenVisitor implements TypeElementVisitor<Object, Object> {

    private final SimpleSerdeShapeAnalyzer analyzer = new SimpleSerdeShapeAnalyzer();
    private @Nullable SourceGenerator sourceGenerator;
    private final RecordSerdeShapeResolver recordSerdeShapeResolver = new RecordSerdeShapeResolver();
    private final BeanSerdeShapeResolver beanSerdeShapeResolver = new BeanSerdeShapeResolver();
    private final EnumSerdeShapeResolver enumSerdeShapeResolver = new EnumSerdeShapeResolver();
    private final Set<String> writtenGeneratedClassNames = new HashSet<>(32);

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(
            Serdeable.class.getName(),
            SerdeableGenerated.class.getName(),
            Serdeable.Serializable.class.getName(),
            Serdeable.Deserializable.class.getName(),
            "jakarta.xml.bind.annotation.XmlRootElement",
            "jakarta.xml.bind.annotation.XmlType",
            "jakarta.xml.bind.annotation.XmlEnum",
            "jakarta.xml.bind.annotation.XmlAccessorOrder",
            "jakarta.xml.bind.annotation.XmlAccessorType"
        );
    }

    @Override
    public void start(VisitorContext visitorContext) {
        // The generated serdes are modeled on Java source and are not valid Kotlin: the Kotlin source
        // declares raw types, such as the Argument constructor parameter, which KSP cannot resolve. Kotlin
        // types use the runtime serdes, even when another processor puts a Kotlin Sourcegen backend on the
        // KSP classpath.
        if (visitorContext.getLanguage() == VisitorContext.Language.KOTLIN) {
            sourceGenerator = null;
            return;
        }
        sourceGenerator = SourceGenerators.findByLanguage(visitorContext.getLanguage()).orElse(null);
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (sourceGenerator == null || element.isPrimitive() || element.isArray() || element.isPrivate()) {
            return;
        }
        if (!element.hasAnnotation(Serdeable.class)
            && !element.hasAnnotation(SerdeableGenerated.class)
            && !element.hasAnnotation(Serdeable.Serializable.class)
            && !element.hasAnnotation(Serdeable.Deserializable.class)
            && !element.hasAnnotation("jakarta.xml.bind.annotation.XmlRootElement")
            && !element.hasAnnotation("jakarta.xml.bind.annotation.XmlType")
            && !element.hasAnnotation("jakarta.xml.bind.annotation.XmlEnum")
            && !element.hasAnnotation("jakarta.xml.bind.annotation.XmlAccessorOrder")
            && !element.hasAnnotation("jakarta.xml.bind.annotation.XmlAccessorType")) {
            return;
        }
        for (ClassDef classDef : generate(element, context.getLanguage())) {
            write(context, element, classDef);
        }
    }

    /**
     * Builds the definitions of the serializer and deserializer generated for the given type.
     *
     * @param element The serdeable type
     * @param language The language of the generated source
     * @return The generated class definitions, empty when both directions use the runtime serdes
     */
    @Internal
    public List<ClassDef> generate(ClassElement element, VisitorContext.Language language) {
        SimpleSerdeShapeDecision decision = analyzer.analyze(element);
        List<ClassDef> classDefs = new ArrayList<>(2);
        if (decision.serializerEligible()) {
            classDefs.add(serializerClass(element, decision));
        }
        if (decision.deserializerEligible()) {
            classDefs.add(deserializerClass(element, decision, language));
        }
        return classDefs;
    }

    private ClassDef serializerClass(ClassElement element, SimpleSerdeShapeDecision decision) {
        String generatedSerializerClassName = SerdeSourceGenClassNaming.generatedSerializerClassName(element);
        if (isConstructorBound(decision)) {
            RecordSerdeShape recordSerdeShape = recordSerdeShapeResolver.resolve(element).orElse(null);
            if (recordSerdeShape != null) {
                return new RecordSerializerSourceGen().generate(element, recordSerdeShape);
            }
        }
        if (decision.shapeKind() == SimpleSerdeShapeDecision.ShapeKind.DEFAULT_CONSTRUCTOR_BEAN) {
            BeanSerdeShape beanSerdeShape = beanSerdeShapeResolver.resolve(element).orElse(null);
            if (beanSerdeShape != null) {
                return new BeanSerializerSourceGen().generate(element, beanSerdeShape);
            }
        }
        if (decision.shapeKind() == SimpleSerdeShapeDecision.ShapeKind.ENUM) {
            EnumSerdeShape enumSerdeShape = enumSerdeShapeResolver.resolve(element).orElse(null);
            if (enumSerdeShape != null) {
                return new EnumSerializerSourceGen().generate(element, enumSerdeShape);
            }
        }
        return ClassDef.builder(generatedSerializerClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember("value", "Micronaut")
                .build())
            .addSuperinterface(TypeDef.parameterized(Serializer.class, TypeDef.of(element)))
            .build();
    }

    private ClassDef deserializerClass(ClassElement element, SimpleSerdeShapeDecision decision, VisitorContext.Language language) {
        String generatedDeserializerClassName = SerdeSourceGenClassNaming.generatedDeserializerClassName(element);
        if (isConstructorBound(decision)) {
            RecordSerdeShape recordSerdeShape = recordSerdeShapeResolver.resolve(element).orElse(null);
            if (recordSerdeShape != null) {
                return new RecordDeserializerSourceGen(language).generate(element, recordSerdeShape);
            }
        }
        if (decision.shapeKind() == SimpleSerdeShapeDecision.ShapeKind.DEFAULT_CONSTRUCTOR_BEAN) {
            BeanSerdeShape beanSerdeShape = beanSerdeShapeResolver.resolve(element).orElse(null);
            if (beanSerdeShape != null) {
                return new BeanDeserializerSourceGen(language).generate(element, beanSerdeShape);
            }
        }
        if (decision.shapeKind() == SimpleSerdeShapeDecision.ShapeKind.ENUM) {
            EnumSerdeShape enumSerdeShape = enumSerdeShapeResolver.resolve(element).orElse(null);
            if (enumSerdeShape != null) {
                return new EnumDeserializerSourceGen().generate(element, enumSerdeShape);
            }
        }
        return ClassDef.builder(generatedDeserializerClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember("value", "Micronaut")
                .build())
            .addSuperinterface(TypeDef.parameterized(Deserializer.class, TypeDef.of(element)))
            .build();
    }

    private static boolean isConstructorBound(SimpleSerdeShapeDecision decision) {
        return decision.shapeKind() == SimpleSerdeShapeDecision.ShapeKind.RECORD
            || decision.shapeKind() == SimpleSerdeShapeDecision.ShapeKind.CONSTRUCTOR_BEAN;
    }

    private void write(VisitorContext context, ClassElement element, ClassDef classDef) {
        SourceGenerator generator = sourceGenerator;
        if (generator == null) {
            return;
        }
        String generatedClassName = classDef.getName();
        if (writtenGeneratedClassNames.contains(generatedClassName)) {
            return;
        }
        try {
            generator.write(classDef, context, element);
            writtenGeneratedClassNames.add(generatedClassName);
        } catch (Exception e) {
            SourceGenerators.handleFatalException(element, Serdeable.class, e, runtimeException -> {
                throw runtimeException;
            });
        }
    }

    /**
     * Visitors run from the highest order to the lowest. The generated serdes have to observe the
     * metadata {@code SerdeAnnotationVisitor} adds to the properties, such as names resolved through a
     * naming strategy or properties ignored through a type-level annotation, so this visitor runs after
     * it and before the introspection is written.
     */
    @Override
    public int getOrder() {
        return IntrospectedTypeElementVisitor.POSITION + 50;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
