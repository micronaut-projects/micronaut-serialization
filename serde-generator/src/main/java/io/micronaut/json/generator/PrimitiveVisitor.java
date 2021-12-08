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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.json.generator.symbol.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * Special visitor that generates serializers for primitive classes in the package of the single class annotated with {@value TYPE}
 */
@Internal
public class PrimitiveVisitor extends AbstractGeneratorVisitor<Object> implements TypeElementVisitor<Object, Object> {
    private static final String TYPE = "io.micronaut.json.generated.serializer.GeneratePrimitiveSerializers";

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(TYPE);
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.hasStereotype(TYPE)) {
            return;
        }

        SerializerLinker linker = new SerializerLinker(context);
        for (ClassElement prim : Arrays.asList(
                PrimitiveElement.BOOLEAN,
                PrimitiveElement.BYTE,
                PrimitiveElement.SHORT,
                PrimitiveElement.CHAR,
                PrimitiveElement.INT,
                PrimitiveElement.LONG,
                PrimitiveElement.FLOAT,
                PrimitiveElement.DOUBLE
        )) {
            generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(GeneratorType.ofClass(prim))
                    .originatingElement(element)
                    .problemReporter(problemReporter)
                    .packageName(element.getPackageName())
                    .valueReferenceName(PoetUtil.toTypeName(prim).box())
                    .linker(linker)
                    .generateMulti());
        }

        for (Class<?> t : Arrays.asList(
                String.class,
                CharSequence.class,
                BigDecimal.class,
                BigInteger.class
        )) {
            generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(GeneratorType.ofClass(ClassElement.of(t)))
                    .originatingElement(element)
                    .problemReporter(problemReporter)
                    .packageName(element.getPackageName())
                    .linker(linker)
                    .generateMulti());
        }

        for (Class<?> t : Arrays.asList(
                // need one for Collection too, because deserializer matching is invariant, so the Deserializer<List<E>> won't be reused.
                Collection.class,
                List.class,
                Set.class,
                SortedSet.class
        )) {
            generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(GeneratorType.ofParameterized(t, (Class<?>) null))
                    .originatingElement(element)
                    .problemReporter(problemReporter)
                    .packageName(element.getPackageName())
                    .linker(linker)
                    .generateMulti());
        }

        generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(GeneratorType.ofParameterized(Map.class, String.class, null))
                .originatingElement(element)
                .problemReporter(problemReporter)
                .packageName(element.getPackageName())
                .linker(linker)
                .generateMulti());
        generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(GeneratorType.ofParameterized(Optional.class, (Class<?>) null))
                .originatingElement(element)
                .problemReporter(problemReporter)
                .packageName(element.getPackageName())
                .linker(linker)
                .generateMulti());

        // Serializer<T[]>
        generateFromSymbol(context, problemReporter -> SingletonSerializerGenerator.create(GeneratorType.GENERIC_ARRAY)
                .originatingElement(element)
                .problemReporter(problemReporter)
                .packageName(element.getPackageName())
                .linker(linker)
                .generateMulti());
    }

}
