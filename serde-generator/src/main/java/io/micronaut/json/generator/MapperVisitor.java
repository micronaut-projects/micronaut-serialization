/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.json.generator;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.json.annotation.SerializableBean;
import io.micronaut.json.annotation.SerializationMixin;
import io.micronaut.json.generator.symbol.*;
import io.micronaut.json.generator.symbol.bean.DependencyGraphChecker;
import io.micronaut.json.generator.symbol.bean.InlineBeanSerializerSymbol;

import java.util.*;

@Internal
public class MapperVisitor extends AbstractGeneratorVisitor<Object> implements TypeElementVisitor<Object, Object> {
    @Override
    public Set<String> getSupportedAnnotationNames() {
        return new HashSet<>(Arrays.asList(
                SerializationMixin.class.getName(),
                SerializationMixin.Repeated.class.getName(),
                SerializableBean.class.getName()
        ));
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visitNormal(element, context);

        AnnotationValue<SerializationMixin.Repeated> repeatedMixin = element.getAnnotation(SerializationMixin.Repeated.class);
        if (repeatedMixin != null) {
            for (AnnotationValue<SerializationMixin> value : repeatedMixin.<SerializationMixin>getAnnotations("value")) {
                visitMixin(element, context, value);
            }
        }
    }

    private void visitNormal(ClassElement element, VisitorContext context) {
        GeneratorType generatorType = GeneratorType.ofClass(element);
        SerializerLinker linker = new SerializerLinker(context);
        if (!linker.inlineBean.canSerializeStandalone(generatorType)) {
            return;
        }
        SerializerSymbol symbol = linker.findSymbol(generatorType);
        if (symbol instanceof InjectingSerializerSymbol) {
            symbol = linker.inlineBean;
        }
        generate(element, context, linker, generatorType, symbol);
    }

    private void visitMixin(ClassElement declaring, VisitorContext context, AnnotationValue<SerializationMixin> annotation) {
        Optional<AnnotationClassValue<?>> forClassUnresolved = annotation.annotationClassValue("forClass");
        if (!forClassUnresolved.isPresent()) {
            context.fail("No forClass value in @SerializationMixin", declaring);
            return;
        }
        Optional<ClassElement> forClassResolved = context.getClassElement(forClassUnresolved.get().getName());
        if (!forClassResolved.isPresent()) {
            context.fail("forClass value in @SerializationMixin not found", declaring);
            return;
        }
        ClassElement forClass = forClassResolved.get();

        GeneratorType generatorType = GeneratorType.ofClass(forClass);

        SerializerLinker linker = new SerializerLinker(context);
        SerializerSymbol symbol = linker.findSymbol(generatorType);
        if (symbol instanceof InjectingSerializerSymbol) {
            symbol = linker.inlineBean;
        }
        Optional<AnnotationValue<SerializableBean>> config = annotation.getAnnotation("config", SerializableBean.class);
        if (symbol instanceof InlineBeanSerializerSymbol) {
            symbol = ((InlineBeanSerializerSymbol) symbol).withFixedAnnotation(config.orElseGet(() -> AnnotationValue.builder(SerializableBean.class).build()));
        } else {
            if (config.isPresent()) {
                context.fail("Cannot set config for mixin that isn't using the bean serializer", declaring);
                return;
            }
        }
        generate(declaring, context, linker, generatorType, symbol);
    }

    private void generate(ClassElement declaring, VisitorContext context, SerializerLinker linker, GeneratorType generatorType, SerializerSymbol symbol) {
        if (symbol instanceof InlineBeanSerializerSymbol) {
            DependencyGraphChecker depChecker = new DependencyGraphChecker(context, linker);
            depChecker.checkCircularDependencies(symbol, generatorType, generatorType.getClassElement());
            if (depChecker.hasAnyFailures()) {
                return;
            }
        }

        generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(generatorType)
                .originatingElement(declaring)
                .packageName(declaring.getPackageName())
                .problemReporter(problemReporter)
                .symbol(symbol)
                .generateSerializer(!(symbol instanceof InlineBeanSerializerSymbol) || ((InlineBeanSerializerSymbol) symbol).supportsDirection(generatorType, true))
                .generateDeserializer(!(symbol instanceof InlineBeanSerializerSymbol) || ((InlineBeanSerializerSymbol) symbol).supportsDirection(generatorType, false))
                .generateMulti());
    }
}
