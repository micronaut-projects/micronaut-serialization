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
package io.micronaut.json.generator.symbol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.Decoder;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;

import java.lang.reflect.TypeVariable;
import java.util.*;

/**
 * {@link SerializerSymbol} that deserializes iterables (and arrays) inline, i.e. without a separate
 * {@link Serializer} implementation.
 */
abstract class InlineIterableSerializerSymbol extends AbstractInlineContainerSerializerSymbol implements SerializerSymbol {

    InlineIterableSerializerSymbol(SerializerLinker linker) {
        super(linker);
    }

    private InlineIterableSerializerSymbol(InlineIterableSerializerSymbol original, boolean recursiveSerialization) {
        super(original, recursiveSerialization);
    }

    @NonNull
    protected abstract GeneratorType getElementType(GeneratorType type);

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        if (visitor.visitStructure()) {
            GeneratorType elementType = getElementType(type);
            visitor.visitStructureElement(getElementSymbol(elementType), elementType, null);
        }
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, String encoderVariable, GeneratorType type, CodeBlock readExpression) {
        GeneratorType elementType = getElementType(type);
        SerializerSymbol elementSerializer = getElementSymbol(elementType);
        String itemName = generatorContext.newLocalVariable("item");
        String arrayEncoder = generatorContext.newLocalVariable("arrayEncoder");
        return CodeBlock.builder()
                .addStatement("$T $N = $N.encodeArray()", Encoder.class, arrayEncoder, encoderVariable)
                .beginControlFlow("for ($T $N : $L)", PoetUtil.toTypeName(elementType), itemName, readExpression)
                .add(elementSerializer.serialize(generatorContext.withSubPath("[*]"), arrayEncoder, elementType, CodeBlock.of("$N", itemName)))
                .endControlFlow()
                .addStatement("$N.finishStructure()", arrayEncoder)
                .build();
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, String decoderVariable, GeneratorType type, Setter setter) {
        GeneratorType elementType = getElementType(type);
        SerializerSymbol elementDeserializer = getElementSymbol(elementType);

        String intermediateVariable = generatorContext.newLocalVariable("intermediate");
        String elementDecoderVariable = generatorContext.newLocalVariable("arrayDecoder");

        CodeBlock.Builder block = CodeBlock.builder();
        block.addStatement("$T $N = $N.decodeArray()", Decoder.class, elementDecoderVariable, decoderVariable);
        block.add(createIntermediate(elementType, intermediateVariable));
        block.beginControlFlow("while ($N.hasNextArrayValue())", elementDecoderVariable);
        block.add(elementDeserializer.deserialize(generatorContext, elementDecoderVariable, elementType, expr -> CodeBlock.of("$N.add($L);\n", intermediateVariable, expr)));
        block.endControlFlow();
        block.addStatement("$N.finishStructure()", elementDecoderVariable);
        block.add(setter.createSetStatement(finishDeserialize(elementType, intermediateVariable)));
        return block.build();
    }

    protected abstract CodeBlock finishDeserialize(GeneratorType elementType, String intermediateVariable);

    protected abstract CodeBlock createIntermediate(GeneratorType elementType, String intermediateVariable);

    static class ArrayImpl extends InlineIterableSerializerSymbol {
        ArrayImpl(SerializerLinker linker) {
            super(linker);
        }

        private ArrayImpl(ArrayImpl original, boolean recursiveSerialization) {
            super(original, recursiveSerialization);
        }

        @Override
        public SerializerSymbol withRecursiveSerialization() {
            return new ArrayImpl(this, true);
        }

        @Nullable
        @Override
        public ConditionExpression<CodeBlock> shouldIncludeCheck(GeneratorContext generatorContext, GeneratorType type, JsonInclude.Include inclusionPolicy) {
            if (inclusionPolicy == JsonInclude.Include.NON_EMPTY) {
                return ConditionExpression.of(expr -> CodeBlock.of("$L.length != 0", expr));
            }
            return super.shouldIncludeCheck(generatorContext, type, inclusionPolicy);
        }

        @Override
        public boolean canSerialize(GeneratorType type) {
            return type.isArray();
        }

        @Override
        @NonNull
        protected GeneratorType getElementType(GeneratorType type) {
            return type.fromArray();
        }

        @Override
        protected CodeBlock finishDeserialize(GeneratorType elementType, String intermediateVariable) {
            // cast for generic arrays
            return CodeBlock.of("$N.toArray(($T[]) new $T[0])", intermediateVariable, PoetUtil.toTypeName(elementType), PoetUtil.toTypeName(elementType.getRawClass()));
        }

        @Override
        protected CodeBlock createIntermediate(GeneratorType elementType, String intermediateVariable) {
            return CodeBlock.of("$T<$T> $N = new $T<>();\n", ArrayList.class, PoetUtil.toTypeName(elementType), intermediateVariable, ArrayList.class);
        }
    }

    static class CollectionImpl extends InlineIterableSerializerSymbol {
        private final Class<?> primaryType;
        private final Map<Class<?>, String> supportedClassesAndTypeVariableNames;

        CollectionImpl(SerializerLinker linker, Class<?>... supportedClasses) {
            super(linker);
            this.supportedClassesAndTypeVariableNames = new HashMap<>();
            this.primaryType = supportedClasses[0];
            try {
                primaryType.getConstructor();
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Primary type must have no-args constructor", e);
            }
            for (Class<?> supportedClass : supportedClasses) {
                if (!supportedClass.isAssignableFrom(primaryType)) {
                    throw new IllegalArgumentException("Primary type must come first");
                }
                TypeVariable<? extends Class<?>>[] typeParameters = supportedClass.getTypeParameters();
                if (typeParameters.length != 1) {
                    throw new IllegalArgumentException("Supported classes must have exactly one type parameter");
                }
                supportedClassesAndTypeVariableNames.put(supportedClass, typeParameters[0].getName());
            }
        }

        private CollectionImpl(CollectionImpl original, boolean recursiveSerialization) {
            super(original, recursiveSerialization);
            this.primaryType = original.primaryType;
            this.supportedClassesAndTypeVariableNames = original.supportedClassesAndTypeVariableNames;
        }

        @Override
        public SerializerSymbol withRecursiveSerialization() {
            return new CollectionImpl(this, true);
        }

        @Override
        public boolean canSerialize(GeneratorType type) {
            for (Class<?> sup : supportedClassesAndTypeVariableNames.keySet()) {
                if (type.isRawTypeEquals(sup)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        @NonNull
        protected GeneratorType getElementType(GeneratorType type) {
            /* todo: bug in getTypeArguments(class)? only returns java.lang.Object
            return type.getTypeArguments(Iterable.class).get("T");
            */
            for (Map.Entry<Class<?>, String> entry : supportedClassesAndTypeVariableNames.entrySet()) {
                Optional<Map<String, GeneratorType>> args = type.getTypeArgumentsExact(entry.getKey());
                if (args.isPresent()) {
                    return args.get().get(entry.getValue());
                }
            }
            throw new UnsupportedOperationException("unsupported type");
        }

        @Override
        protected CodeBlock finishDeserialize(GeneratorType elementType, String intermediateVariable) {
            return CodeBlock.of("$N", intermediateVariable);
        }

        @Override
        protected CodeBlock createIntermediate(GeneratorType elementType, String intermediateVariable) {
            return CodeBlock.of("$T<$T> $N = new $T<>();\n", primaryType, PoetUtil.toTypeName(elementType), intermediateVariable, primaryType);
        }
    }
}
