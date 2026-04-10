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
import io.micronaut.sourcegen.generator.visitors.BuilderAnnotationVisitor;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.Generated;
import javax.lang.model.element.Modifier;
import java.util.HashSet;
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
            Serdeable.Deserializable.class.getName()
        );
    }

    @Override
    public void start(VisitorContext visitorContext) {
        sourceGenerator = SourceGenerators.findByLanguage(visitorContext.getLanguage()).orElse(null);
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (sourceGenerator == null || element.isPrimitive() || element.isArray() || element.isPrivate()) {
            return;
        }
        if (!element.hasDeclaredAnnotation(Serdeable.class)
            && !element.hasDeclaredAnnotation(SerdeableGenerated.class)
            && !element.hasDeclaredAnnotation(Serdeable.Serializable.class)
            && !element.hasDeclaredAnnotation(Serdeable.Deserializable.class)) {
            return;
        }
        SimpleSerdeShapeDecision decision = analyzer.analyze(element);
        if (decision.serializerEligible()) {
            generateSerializerClass(element, decision, context);
        }
        if (decision.deserializerEligible()) {
            generateDeserializerClass(element, decision, context);
        }
    }

    private void generateSerializerClass(ClassElement element, SimpleSerdeShapeDecision decision, VisitorContext context) {
        String generatedSerializerClassName = SerdeSourceGenClassNaming.generatedSerializerClassName(element);
        if (decision.shapeKind() == SimpleSerdeShapeDecision.ShapeKind.RECORD) {
            RecordSerdeShape recordSerdeShape = recordSerdeShapeResolver.resolve(element).orElse(null);
            if (recordSerdeShape != null) {
                write(context, element, generatedSerializerClassName, new RecordSerializerSourceGen().generate(element, recordSerdeShape));
                return;
            }
        }
        if (decision.shapeKind() == SimpleSerdeShapeDecision.ShapeKind.DEFAULT_CONSTRUCTOR_BEAN) {
            BeanSerdeShape beanSerdeShape = beanSerdeShapeResolver.resolve(element).orElse(null);
            if (beanSerdeShape != null) {
                write(context, element, generatedSerializerClassName, new BeanSerializerSourceGen().generate(element, beanSerdeShape));
                return;
            }
        }
        if (decision.shapeKind() == SimpleSerdeShapeDecision.ShapeKind.ENUM) {
            EnumSerdeShape enumSerdeShape = enumSerdeShapeResolver.resolve(element).orElse(null);
            if (enumSerdeShape != null) {
                write(context, element, generatedSerializerClassName, new EnumSerializerSourceGen().generate(element, enumSerdeShape));
                return;
            }
        }
        write(context, element, generatedSerializerClassName, ClassDef.builder(generatedSerializerClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember("value", "Micronaut")
                .build())
            .addSuperinterface(TypeDef.parameterized(Serializer.class, TypeDef.of(element)))
            .build());
    }

    private void generateDeserializerClass(ClassElement element, SimpleSerdeShapeDecision decision, VisitorContext context) {
        String generatedDeserializerClassName = SerdeSourceGenClassNaming.generatedDeserializerClassName(element);
        if (decision.shapeKind() == SimpleSerdeShapeDecision.ShapeKind.RECORD) {
            RecordSerdeShape recordSerdeShape = recordSerdeShapeResolver.resolve(element).orElse(null);
            if (recordSerdeShape != null) {
                write(context, element, generatedDeserializerClassName, new RecordDeserializerSourceGen().generate(element, recordSerdeShape));
                return;
            }
        }
        if (decision.shapeKind() == SimpleSerdeShapeDecision.ShapeKind.DEFAULT_CONSTRUCTOR_BEAN) {
            BeanSerdeShape beanSerdeShape = beanSerdeShapeResolver.resolve(element).orElse(null);
            if (beanSerdeShape != null) {
                write(context, element, generatedDeserializerClassName, new BeanDeserializerSourceGen().generate(element, beanSerdeShape));
                return;
            }
        }
        if (decision.shapeKind() == SimpleSerdeShapeDecision.ShapeKind.ENUM) {
            EnumSerdeShape enumSerdeShape = enumSerdeShapeResolver.resolve(element).orElse(null);
            if (enumSerdeShape != null) {
                write(context, element, generatedDeserializerClassName, new EnumDeserializerSourceGen().generate(element, enumSerdeShape));
                return;
            }
        }
        write(context, element, generatedDeserializerClassName, ClassDef.builder(generatedDeserializerClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addAnnotation(AnnotationDef.builder(Generated.class)
                .addMember("value", "Micronaut")
                .build())
            .addSuperinterface(TypeDef.parameterized(Deserializer.class, TypeDef.of(element)))
            .build());
    }

    private void write(VisitorContext context, ClassElement element, String generatedClassName, ClassDef classDef) {
        SourceGenerator generator = sourceGenerator;
        if (generator == null) {
            return;
        }
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

    @Override
    public int getOrder() {
        return IntrospectedTypeElementVisitor.POSITION + 200;
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
