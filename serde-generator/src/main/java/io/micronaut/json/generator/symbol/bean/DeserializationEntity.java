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
package io.micronaut.json.generator.symbol.bean;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.json.Decoder;
import io.micronaut.json.generator.symbol.ConditionExpression;
import io.micronaut.json.generator.symbol.GeneratorContext;
import io.micronaut.json.generator.symbol.GeneratorType;
import io.micronaut.json.generator.symbol.PoetUtil;
import io.micronaut.json.generator.symbol.SerializerLinker;
import io.micronaut.json.generator.symbol.SerializerSymbol;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract class DeserializationEntity {
    /**
     * An entity that will generate a java object, placing it into a local variable.
     */
    boolean presentAsJava = false;
    /**
     * An entity that consists of multiple json properties.
     */
    boolean hasProperties = false;

    String localVariableName;

    ConditionExpression<String> allowDeserializationCheck = ConditionExpression.alwaysTrue();

    private DeserializationEntity() {
    }

    private static CodeBlock getCreatorCall(GeneratorType type, BeanDefinition definition, CodeBlock creatorParameters) {
        if (definition.creator instanceof ConstructorElement) {
            return CodeBlock.of("new $T($L)", PoetUtil.toTypeName(type), creatorParameters);
        } else if (definition.creator.isStatic()) {
            return CodeBlock.of(
                    "$T.$N($L)",
                    PoetUtil.toTypeName(definition.creator.getDeclaringType()),
                    definition.creator.getName(),
                    creatorParameters
            );
        } else {
            throw new AssertionError("bad creator, should have been detected in BeanIntrospector");
        }
    }

    void allocateLocals(GeneratorContext context, String ownNameHint) {
        if (localVariableName == null && presentAsJava) {
            localVariableName = context.newLocalVariable(ownNameHint);
        }
    }

    void generatePrologue(CodeBlock.Builder builder) {
    }

    void generateEpilogue(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
    }

    CodeBlock deserializeTopLevel(
            GeneratorContext generatorContext,
            String decoderVariable,
            SerializerSymbol.Setter setter
    ) {
        if (!hasProperties) {
            throw new UnsupportedOperationException();
        }
        allocateLocals(generatorContext, "result");

        CodeBlock.Builder builder = CodeBlock.builder();
        generatePrologue(builder);

        InlineBitSet<DeserializationEntity> readProperties;
        Set<DeserializationEntity> propertiesForDuplicateDetection = collectPropertiesForDuplicateDetection();
        if (propertiesForDuplicateDetection.isEmpty()) {
            readProperties = null;
        } else {
            readProperties = new InlineBitSet<>(generatorContext, propertiesForDuplicateDetection, "readProperties");
            readProperties.emitMaskDeclarations(builder);
        }

        String elementDecoderVariable = generatorContext.newLocalVariable("elementDecoder");
        String fieldNameVariable = generatorContext.newLocalVariable("fieldName");

        DeserializationEntity unknownPropertyHandler = new FailUnknownProperties(null);
        for (int i = UnknownPropertyHandlingStage.values().length - 1; i >= 0; i--) {
            DeserializationEntity forStage = onUnknownProperty(generatorContext, UnknownPropertyHandlingStage.values()[i], fieldNameVariable, elementDecoderVariable, unknownPropertyHandler);
            if (forStage != null) {
                unknownPropertyHandler = forStage;
            }
        }

        builder.addStatement("$T $N = $N.decodeObject()", Decoder.class, elementDecoderVariable, decoderVariable);

        // main parse loop
        builder.beginControlFlow("while (true)");
        builder.addStatement("$T $N = $N.decodeKey()", String.class, fieldNameVariable, elementDecoderVariable);
        builder.add("if ($N == null) break;\n", fieldNameVariable);
        builder.beginControlFlow("switch ($N)", fieldNameVariable);

        Map<String, ? extends DeserializationEntity> collectedProperties = collectProperties(generatorContext);
        Map<DeserializationEntity, List<String>> collectedPropertiesReverse = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends DeserializationEntity> entry : collectedProperties.entrySet()) {
            collectedPropertiesReverse.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        for (Map.Entry<DeserializationEntity, List<String>> entry : collectedPropertiesReverse.entrySet()) {
            for (String alias : entry.getValue()) {
                builder.add("case $S:\n", alias);
            }
            builder.indent();
            entry.getKey().deserializeProperty(generatorContext, builder, readProperties, unknownPropertyHandler, elementDecoderVariable, fieldNameVariable);
            builder.addStatement("break");
            builder.unindent();
        }

        // unknown properties
        builder.beginControlFlow("default:");
        unknownPropertyHandler.deserializeProperty(generatorContext, builder, readProperties, null, elementDecoderVariable, fieldNameVariable);
        builder.endControlFlow();

        builder.endControlFlow();
        builder.endControlFlow();

        builder.addStatement("$N.finishStructure()", elementDecoderVariable);

        generateEpilogue(generatorContext, builder, readProperties, decoderVariable);

        builder.add(setter.createSetStatement(CodeBlock.of("$N", localVariableName)));
        return builder.build();
    }

    final void deserializeProperty(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, DeserializationEntity unknownPropertyHandler, String decoderVariable, @Nullable String fieldNameVariable) {
        if (!allowDeserializationCheck.isAlwaysTrue()) {
            builder.beginControlFlow("if ($L)", allowDeserializationCheck.build(decoderVariable));
        }
        deserializeImpl(generatorContext, builder, readProperties, unknownPropertyHandler, decoderVariable, fieldNameVariable);
        if (!allowDeserializationCheck.isAlwaysTrue()) {
            builder.nextControlFlow("else");
            unknownPropertyHandler.deserializeProperty(generatorContext, builder, readProperties, unknownPropertyHandler, decoderVariable, fieldNameVariable);
            builder.endControlFlow();
        }
    }

    void deserializeImpl(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, DeserializationEntity unknownPropertyHandler, String decoderVariable, @Nullable String fieldNameVariable) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    DeserializationEntity onUnknownProperty(
            GeneratorContext generatorContext, UnknownPropertyHandlingStage stage,
            String fieldNameVariable,
            String elementDecoderVariable,
            DeserializationEntity next
    ) {
        return null;
    }

    Map<String, ? extends DeserializationEntity> collectProperties(GeneratorContext context) {
        throw new UnsupportedOperationException();
    }

    Set<DeserializationEntity> collectPropertiesForDuplicateDetection() {
        return Collections.emptySet();
    }

    boolean isStructurallyIdentical(DeserializationEntity other) {
        return this == other;
    }

    void updateChildren(Function<DeserializationEntity, DeserializationEntity> function) {
    }

    static DeserializationEntity introspect(
            InlineBeanSerializerSymbol symbol,
            GeneratorContext generatorContext,
            GeneratorType type,
            ConditionExpression<String> allowDeserializationCheck
    ) {
        BeanDefinition def = symbol.introspect(generatorContext.getProblemReporter(), type, false);
        if (def.creatorDelegatingProperty != null) {
            PropWithType singleProp = PropWithType.fromContext(type, def.creatorDelegatingProperty);
            return new Delegating(type, def, singleProp, SymbolLookup.forProperty(singleProp).lookup(symbol.linker, false));
        } else if (def.subtyping != null) {
            if (def.subtyping.deduce) {
                return new SubtypingFlat(symbol, generatorContext, type, def, allowDeserializationCheck);
            }
            switch (def.subtyping.as) {
                case WRAPPER_ARRAY:
                case WRAPPER_OBJECT:
                    return new SubtypingWrapper(
                            type,
                            def.subtyping.subTypeNames.entrySet().stream()
                                    .collect(Collectors.toMap(e -> introspect(symbol, generatorContext, e.getKey(), allowDeserializationCheck), Map.Entry::getValue)),
                            def.subtyping.as == JsonTypeInfo.As.WRAPPER_ARRAY
                    );
                case PROPERTY:
                    return new SubtypingFlat(symbol, generatorContext, type, def, allowDeserializationCheck);
                default:
                    generatorContext.getProblemReporter().fail("Unsupported subtyping structure " + def.subtyping.as, type.getClassElement());
                    return new SubtypingFlat(symbol, generatorContext, type, def, allowDeserializationCheck);
            }
        } else {
            Map<BeanDefinition.Property, DeserializationEntity> elements = new LinkedHashMap<>();
            for (BeanDefinition.Property prop : def.props) {
                PropWithType propWithType = PropWithType.fromContext(type, prop);
                ConditionExpression<String> propCheck = allowDeserializationCheck;
                if (prop.viewClasses != null) {
                    propCheck = propCheck.and(symbol.checkJsonViewEnabled(prop));
                }
                DeserializationEntity propEntity;
                if (prop.unwrapped) {
                    propEntity = introspect(symbol, generatorContext, propWithType.type, propCheck);
                    if (!propEntity.hasProperties) {
                        generatorContext.getProblemReporter().fail("Invalid unwrapped property", prop.getElement());
                        continue;
                    }
                } else {
                    propEntity = new SimpleLeafProperty(propWithType, SymbolLookup.forProperty(propWithType), symbol.linker);
                }
                propEntity.allowDeserializationCheck = propCheck;
                elements.put(prop, propEntity);
            }
            AnySetterAsMap anySetterAsMap;
            if (def.anySetter != null) {
                ClassElement context = def.anySetter.getDeclaringType().getRawClassElement();
                GeneratorType keyType = GeneratorType.parameterType(def.anySetter.getParameters()[0], type.typeParametersAsFoldFunction(context));
                GeneratorType valueType = GeneratorType.parameterType(def.anySetter.getParameters()[1], type.typeParametersAsFoldFunction(context));
                if (!keyType.isRawTypeEquals(String.class)) {
                    generatorContext.getProblemReporter().fail("First parameter of JsonAnySetter must be string", def.anySetter);
                }
                anySetterAsMap = new AnySetterAsMap(valueType, SymbolLookup.forAnySetterValue(valueType).lookup(symbol.linker, false));
                anySetterAsMap.allowDeserializationCheck = allowDeserializationCheck;
            } else {
                anySetterAsMap = null;
            }
            return new Structure(type, def, elements, anySetterAsMap);
        }
    }

    /**
     * Delegate to a single property. Used for delegating JsonCreator.
     */
    private static class Delegating extends DeserializationEntity {
        final GeneratorType type;
        final BeanDefinition beanDefinition;
        final PropWithType prop;
        final SerializerSymbol symbol;

        Delegating(GeneratorType type, BeanDefinition beanDefinition, PropWithType prop, SerializerSymbol symbol) {
            this.type = type;
            this.beanDefinition = beanDefinition;
            this.prop = prop;
            this.symbol = symbol;

            presentAsJava = true;
        }

        @Override
        void generatePrologue(CodeBlock.Builder builder) {
            builder.addStatement("$T $N;", PoetUtil.toTypeName(type), localVariableName);
        }

        @Override
        CodeBlock deserializeTopLevel(GeneratorContext generatorContext, String decoderVariable, SerializerSymbol.Setter setter) {
            return symbol.deserialize(
                    generatorContext, decoderVariable,
                    prop.type,
                    SerializerSymbol.Setter.delegate(setter, expr -> getCreatorCall(type, beanDefinition, expr)));
        }

        @Override
        public void deserializeImpl(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, DeserializationEntity unknownPropertyHandler, String decoderVariable, @Nullable String fieldNameVariable) {
            builder.add(symbol.deserialize(
                    generatorContext, decoderVariable,
                    prop.type,
                    expr -> CodeBlock.of("$N = $L;\n", localVariableName, getCreatorCall(type, beanDefinition, expr))));
        }
    }

    private static class Structure extends DeserializationEntity {
        private final GeneratorType type;
        private final BeanDefinition definition;
        private final Map<BeanDefinition.Property, DeserializationEntity> elements;
        @Nullable
        private AnySetterAsMap anySetterAsMap;

        private Structure(GeneratorType type, BeanDefinition definition, Map<BeanDefinition.Property, DeserializationEntity> elements, AnySetterAsMap anySetterAsMap) {
            this.type = type;
            this.definition = definition;
            this.elements = elements;
            this.anySetterAsMap = anySetterAsMap;

            presentAsJava = true;
            hasProperties = true;
        }

        @Override
        void updateChildren(Function<DeserializationEntity, DeserializationEntity> function) {
            for (Map.Entry<BeanDefinition.Property, DeserializationEntity> entry : elements.entrySet()) {
                entry.setValue(function.apply(entry.getValue()));
                entry.getValue().updateChildren(function);
            }
            if (anySetterAsMap != null) {
                anySetterAsMap = (AnySetterAsMap) function.apply(anySetterAsMap);
            }
        }

        @Override
        void allocateLocals(GeneratorContext context, String ownNameHint) {
            super.allocateLocals(context, ownNameHint);
            for (Map.Entry<BeanDefinition.Property, DeserializationEntity> element : elements.entrySet()) {
                element.getValue().allocateLocals(context, element.getKey().name);
            }
            if (anySetterAsMap != null) {
                anySetterAsMap.allocateLocals(context, ownNameHint);
            }
        }

        @Override
        void generatePrologue(CodeBlock.Builder builder) {
            for (DeserializationEntity element : elements.values()) {
                element.generatePrologue(builder);
            }
            if (anySetterAsMap != null) {
                anySetterAsMap.generatePrologue(builder);
            }
        }

        @Override
        Map<String, ? extends DeserializationEntity> collectProperties(GeneratorContext context) {
            Map<String, DeserializationEntity> result = new LinkedHashMap<>();
            for (Map.Entry<BeanDefinition.Property, DeserializationEntity> element : elements.entrySet()) {
                if (element.getValue().hasProperties) {
                    for (Map.Entry<String, ? extends DeserializationEntity> childEntry : element.getValue().collectProperties(context).entrySet()) {
                        putProperty(context, result, childEntry.getKey(), childEntry.getValue());
                    }
                } else {
                    putProperty(context, result, element.getKey().name, element.getValue());
                    for (String alias : element.getKey().aliases) {
                        putProperty(context, result, alias, element.getValue());
                    }
                }
            }
            return result;
        }

        private void putProperty(GeneratorContext context, Map<String, DeserializationEntity> map, String name, DeserializationEntity prop) {
            DeserializationEntity old = map.put(name, prop);
            if (old != null) {
                context.getProblemReporter().fail("Duplicate property " + name, type.getClassElement());
            }
        }

        @Override
        Set<DeserializationEntity> collectPropertiesForDuplicateDetection() {
            return elements.values().stream()
                    .flatMap(entity -> entity.collectPropertiesForDuplicateDetection().stream())
                    .collect(Collectors.toSet());
        }

        @Nullable
        @Override
        DeserializationEntity onUnknownProperty(GeneratorContext generatorContext, UnknownPropertyHandlingStage stage, String fieldNameVariable, String elementDecoderVariable, DeserializationEntity next) {
            if (stage == UnknownPropertyHandlingStage.FAIL) {
                return new FailUnknownProperties(type);
            }
            if (stage == UnknownPropertyHandlingStage.IGNORE && definition.ignoreUnknownProperties) {
                return new IgnoreUnknownProperties();
            }
            boolean any = false;
            if (stage == UnknownPropertyHandlingStage.ANY_SETTER && anySetterAsMap != null) {
                next = anySetterAsMap;
                any = true;
            }
            for (DeserializationEntity value : elements.values()) {
                DeserializationEntity nextMaybe = value.onUnknownProperty(generatorContext, stage, fieldNameVariable, elementDecoderVariable, next);
                if (nextMaybe != null) {
                    if (stage.distinct) {
                        generatorContext.getProblemReporter().fail("Can only have one @JsonAnySetter (check @JsonUnwrapped members)", type.getClassElement());
                    }
                    next = nextMaybe;
                    any = true;
                }
            }
            return any ? next : null;
        }

        @Override
        void generateEpilogue(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
            for (DeserializationEntity value : elements.values()) {
                value.generateEpilogue(generatorContext, builder, readProperties, decoderVariable);
            }

            Set<DeserializationEntity> required = definition.props.stream().filter(property -> property.required).map(elements::get).collect(Collectors.toSet());
            if (!required.isEmpty()) {
                // do a best-effort of finding the property names of the individual DeserializationEntities.
                // One entity may be associated with multiple properties
                Map<DeserializationEntity, String> propertyNames = new HashMap<>();
                for (Map.Entry<BeanDefinition.Property, DeserializationEntity> element : elements.entrySet()) {
                    propertyNames.put(element.getValue(), element.getKey().name);
                }
                readProperties.onMissing(builder, required.stream().collect(Collectors.toMap(
                        req -> req,
                        req -> CodeBlock.of("throw $N.createDeserializationException($S);\n",
                                decoderVariable,
                                "Missing property " + propertyNames.get(req))
                )));
            }

            CodeBlock.Builder creatorParameters = CodeBlock.builder();
            boolean firstParameter = true;
            for (BeanDefinition.Property prop : definition.creatorProps) {
                if (!firstParameter) {
                    creatorParameters.add(", ");
                }
                creatorParameters.add("$L", elements.get(prop).localVariableName);
                firstParameter = false;
            }

            builder.addStatement("$T $N = $L", PoetUtil.toTypeName(type), localVariableName, getCreatorCall(type, definition, creatorParameters.build()));
            for (BeanDefinition.Property prop : definition.props) {
                // unwrapped properties are created in in their specific epilogues, required properties are checked to be present above
                CodeBlock expressionHasBeenRead = prop.unwrapped || prop.required ? null : readProperties.isSet(elements.get(prop));

                if (expressionHasBeenRead != null) {
                    // don't set a property we haven't read
                    builder.beginControlFlow("if ($L)", expressionHasBeenRead);
                }

                String elementVariable = elements.get(prop).localVariableName;
                if (prop.setter != null) {
                    builder.addStatement("$N.$N($N)", localVariableName, prop.setter.getName(), elementVariable);
                } else if (prop.field != null) {
                    builder.addStatement("$N.$N = $N", localVariableName, prop.field.getName(), elementVariable);
                } else {
                    if (prop.creatorParameter == null) {
                        throw new AssertionError("Cannot set property, should have been filtered out during introspection");
                    }
                }

                if (expressionHasBeenRead != null) {
                    builder.endControlFlow();
                }
            }

            if (anySetterAsMap != null) {
                String entryName = generatorContext.newLocalVariable("anySetterEntry");
                builder.beginControlFlow("for ($T<$T, $T> $N : $N.entrySet())", Map.Entry.class, String.class, PoetUtil.toTypeName(anySetterAsMap.valueType), entryName, anySetterAsMap.localVariableName);
                builder.addStatement("$N.$N($N.getKey(), $N.getValue())", localVariableName, definition.anySetter.getName(), entryName, entryName);
                builder.endControlFlow();
            }
        }
    }

    private static class SimpleLeafProperty extends DeserializationEntity {
        private final PropWithType prop;
        private final SymbolLookup symbolLookup;
        private final SerializerLinker linker;

        private boolean declared = false;

        SimpleLeafProperty(PropWithType prop, SymbolLookup symbolLookup, SerializerLinker linker) {
            this.prop = prop;
            this.symbolLookup = symbolLookup;
            this.linker = linker;

            presentAsJava = true;
        }

        @Override
        void generatePrologue(CodeBlock.Builder builder) {
            if (!declared) {
                builder.addStatement("$T $N = $L", PoetUtil.toTypeName(prop.type), localVariableName, symbolLookup.lookup(linker, false).getDefaultExpression(prop.type));
                declared = true;
            }
        }

        @Override
        Set<DeserializationEntity> collectPropertiesForDuplicateDetection() {
            return Collections.singleton(this);
        }

        @Override
        boolean isStructurallyIdentical(DeserializationEntity other) {
            return other instanceof SimpleLeafProperty &&
                    this.symbolLookup.equals(((SimpleLeafProperty) other).symbolLookup) &&
                    this.allowDeserializationCheck.equals(other.allowDeserializationCheck);
        }

        @Override
        void deserializeImpl(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, DeserializationEntity unknownPropertyHandler, String decoderVariable, @Nullable String fieldNameVariable) {
            builder.add(
                    "if ($L) throw $N.createDeserializationException($S);\n",
                    readProperties.isSet(this),
                    decoderVariable,
                    "Duplicate property " + prop.property.name
            );
            readProperties.set(builder, this);

            CodeBlock deserializationCode = symbolLookup.lookup(linker, false)
                    .deserialize(generatorContext.withSubPath(prop.property.name), decoderVariable, prop.type, expr -> CodeBlock.of("$N = $L;\n", localVariableName, expr));
            builder.add(deserializationCode);
        }
    }

    private static class AnySetterAsMap extends DeserializationEntity {
        private final GeneratorType valueType;
        private final SerializerSymbol valueSymbol;

        private boolean declared = false;

        AnySetterAsMap(GeneratorType valueType, SerializerSymbol valueSymbol) {
            this.valueType = valueType;
            this.valueSymbol = valueSymbol;

            this.presentAsJava = true;
        }

        @Override
        void generatePrologue(CodeBlock.Builder builder) {
            if (!declared) {
                builder.addStatement("$T<$T, $T> $N = new $T<>()", Map.class, String.class, PoetUtil.toTypeName(valueType), localVariableName, LinkedHashMap.class);
                declared = true;
            }
        }

        @Override
        boolean isStructurallyIdentical(DeserializationEntity other) {
            return other instanceof AnySetterAsMap &&
                    this.valueType.typeEquals(((AnySetterAsMap) other).valueType) &&
                    this.allowDeserializationCheck.equals(other.allowDeserializationCheck);
        }

        @Override
        void deserializeImpl(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, DeserializationEntity unknownPropertyHandler, String decoderVariable, @Nullable String fieldNameVariable) {
            CodeBlock deserializationCode = valueSymbol
                    .deserialize(generatorContext.withSubPath("[AnySetter]"), decoderVariable, valueType, expr -> CodeBlock.of("$N.put($N, $L);\n", localVariableName, fieldNameVariable, expr));
            builder.add(deserializationCode);
        }
    }

    /**
     * WRAPPER_OBJECT, WRAPPER_ARRAY
     */
    private static class SubtypingWrapper extends DeserializationEntity {
        private final GeneratorType superType;
        private final Map<DeserializationEntity, Collection<String>> subTypes;
        private final boolean array;

        SubtypingWrapper(GeneratorType superType, Map<DeserializationEntity, Collection<String>> subTypes, boolean array) {
            this.superType = superType;
            this.subTypes = subTypes;
            this.array = array;

            hasProperties = false;
            presentAsJava = false; // don't support saving to a local variable
        }

        @Override
        CodeBlock deserializeTopLevel(GeneratorContext generatorContext, String decoderVariable, SerializerSymbol.Setter setter) {
            CodeBlock.Builder builder = CodeBlock.builder();

            String tmpVariable;
            SerializerSymbol.Setter tmpSetter;
            if (setter.terminatesBlock()) {
                tmpVariable = generatorContext.newLocalVariable("result");
                builder.addStatement("$T $N;", PoetUtil.toTypeName(superType), tmpVariable);
                tmpSetter = expr -> CodeBlock.of("$N = $L;\n", tmpVariable, expr);
            } else {
                tmpVariable = null;
                tmpSetter = setter;
            }

            String wrapperDecoder = generatorContext.newLocalVariable("wrapperDecoder");
            builder.addStatement("$T $N = $N.$N()", Decoder.class, wrapperDecoder, decoderVariable, array ? "decodeArray" : "decodeObject");

            if (array) {
                builder.beginControlFlow("switch ($N.decodeString())", wrapperDecoder);
            } else {
                String tagVar = generatorContext.newLocalVariable("tag");
                builder.addStatement("$T $N = $N.decodeKey()", String.class, tagVar, wrapperDecoder);
                builder.addStatement("if ($N == null) throw $N.createDeserializationException(\"Expected type tag, but got object end\")", tagVar, wrapperDecoder);
                builder.beginControlFlow("switch ($N)", tagVar);
            }
            for (Map.Entry<DeserializationEntity, Collection<String>> entry : subTypes.entrySet()) {
                for (String alias : entry.getValue()) {
                    builder.add("case $S:\n", alias);
                }
                builder.indent();
                builder.add(entry.getKey().deserializeTopLevel(generatorContext, wrapperDecoder, tmpSetter));
                builder.addStatement("break");
                builder.unindent();
            }
            builder.add("default:\n");
            builder.indent();
            builder.addStatement("throw $N.createDeserializationException(\"Unknown type tag\")", wrapperDecoder);
            builder.unindent();
            builder.endControlFlow();

            builder.addStatement("$N.finishStructure()", wrapperDecoder);

            if (tmpVariable != null) {
                builder.add(setter.createSetStatement(CodeBlock.of("$L", tmpVariable)));
            }

            return builder.build();
        }
    }

    /**
     * PROPERTY, DEDUCTION
     */
    private static class SubtypingFlat extends DeserializationEntity {
        private final GeneratorType superType;
        private final Collection<DeserializationEntity> subTypes;

        private final InlineBitSet<DeserializationEntity> possibleTypes;

        private final String tagPropertyName;
        @Nullable
        private final TypeTagProperty tagProperty;

        private final Map<String, AmbiguousProperty> ambiguousProperties;

        SubtypingFlat(
                InlineBeanSerializerSymbol symbol,
                GeneratorContext context,
                GeneratorType superType,
                BeanDefinition definition,
                ConditionExpression<String> allowDeserializationCheck) {
            this.superType = superType;
            Map<GeneratorType, DeserializationEntity> subTypeEntities = definition.subtyping.subTypes.stream()
                    .collect(Collectors.toMap(t -> t, t -> introspect(symbol, context, t, allowDeserializationCheck)));
            this.subTypes = subTypeEntities.values();

            tagPropertyName = definition.subtyping.propertyName;
            if (definition.subtyping.deduce) {
                tagProperty = null;
            } else {
                tagProperty = new TypeTagProperty(definition.subtyping.subTypeNames.entrySet().stream()
                        .collect(Collectors.toMap(e -> subTypeEntities.get(e.getKey()), Map.Entry::getValue)));
            }

            possibleTypes = new InlineBitSet<>(context, subTypes, "possibleTypes_" + superType.getTypeName());

            ambiguousProperties = new LinkedHashMap<>();
            for (DeserializationEntity subType : subTypes) {
                Map<String, ? extends DeserializationEntity> subTypeProperties = subType.collectProperties(context);
                for (Map.Entry<String, ? extends DeserializationEntity> entry : subTypeProperties.entrySet()) {
                    ambiguousProperties.computeIfAbsent(entry.getKey(), k -> new AmbiguousProperty())
                            .paths.add(new Path(subType, entry.getValue()));
                }
            }
            for (AmbiguousProperty ambiguousProperty : ambiguousProperties.values()) {
                ambiguousProperty.unite();
            }

            hasProperties = true;
            presentAsJava = true;
        }

        @Override
        void updateChildren(Function<DeserializationEntity, DeserializationEntity> function) {
            for (DeserializationEntity subType : subTypes) {
                subType.updateChildren(function);
            }
        }

        @Override
        void allocateLocals(GeneratorContext context, String ownNameHint) {
            super.allocateLocals(context, ownNameHint);

            for (DeserializationEntity subType : subTypes) {
                subType.allocateLocals(context, "subType");
            }
        }

        @Override
        void generatePrologue(CodeBlock.Builder builder) {
            possibleTypes.emitMaskDeclarations(builder, true);
            for (DeserializationEntity subType : subTypes) {
                subType.generatePrologue(builder);
            }
        }

        @Override
        Map<String, ? extends DeserializationEntity> collectProperties(GeneratorContext context) {
            if (tagProperty != null) {
                Map<String, DeserializationEntity> all = new LinkedHashMap<>();
                all.put(tagPropertyName, tagProperty);
                all.putAll(ambiguousProperties);
                return all;
            } else {
                return ambiguousProperties;
            }
        }

        @Override
        Set<DeserializationEntity> collectPropertiesForDuplicateDetection() {
            return subTypes.stream()
                    .flatMap(t -> t.collectPropertiesForDuplicateDetection().stream())
                    .collect(Collectors.toSet());
        }

        @Override
        DeserializationEntity onUnknownProperty(GeneratorContext generatorContext, UnknownPropertyHandlingStage stage, String fieldNameVariable, String elementDecoderVariable, DeserializationEntity next) {
            if (stage == UnknownPropertyHandlingStage.FAIL) {
                return new FailUnknownProperties(superType);
            }
            AmbiguousProperty unknownPropertyHandler = new AmbiguousProperty();
            for (DeserializationEntity subType : subTypes) {
                DeserializationEntity subTypeUnknownPropertyHandler = subType.onUnknownProperty(generatorContext, stage, fieldNameVariable, elementDecoderVariable, next);
                unknownPropertyHandler.paths.add(new Path(subType, subTypeUnknownPropertyHandler == null ? next : subTypeUnknownPropertyHandler));
            }
            unknownPropertyHandler.unite();
            if (unknownPropertyHandler.paths.stream().allMatch(p -> p.property.isStructurallyIdentical(next))) {
                return null;
            } else {
                return unknownPropertyHandler;
            }
        }

        @Override
        void generateEpilogue(GeneratorContext context, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
            builder.addStatement("$T $N", PoetUtil.toTypeName(superType), localVariableName);

            builder.beginControlFlow("if ($L > 1)", possibleTypes.bitCount());
            builder.addStatement("throw $N.createDeserializationException(\"Ambiguous type\")", decoderVariable);
            for (DeserializationEntity subType : subTypes) {
                builder.nextControlFlow("else if ($L)", possibleTypes.isSet(subType));
                subType.generateEpilogue(context, builder, readProperties, decoderVariable);
                builder.addStatement("$N = $N", localVariableName, subType.localVariableName);
            }
            builder.nextControlFlow("else");
            builder.addStatement("throw $N.createDeserializationException(\"No matching type candidate\")", decoderVariable);
            builder.endControlFlow();
        }

        private class AmbiguousProperty extends DeserializationEntity {
            final List<Path> paths = new ArrayList<>();

            @Override
            void generatePrologue(CodeBlock.Builder builder) {
                throw new UnsupportedOperationException();
            }

            /**
             * Unite properties of different types that are structurally identical
             */
            void unite() {
                // this is potentially n² in the number of subtypes, but only if they have identically named properties
                // that all differ structurally
                for (int i = 0; i < paths.size(); i++) {
                    Path from = paths.get(i);
                    for (int j = 0; j < i; j++) {
                        Path into = paths.get(j);
                        if (into.mergeFrom(from)) {
                            paths.remove(i);
                            i--;
                            break;
                        }
                    }
                }
            }

            @Override
            void generateEpilogue(GeneratorContext context, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, String decoderVariable) {
                throw new UnsupportedOperationException();
            }

            @Override
            Set<DeserializationEntity> collectPropertiesForDuplicateDetection() {
                return paths.stream().flatMap(p -> p.property.collectPropertiesForDuplicateDetection().stream()).collect(Collectors.toSet());
            }

            @Override
            void deserializeImpl(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, DeserializationEntity unknownPropertyHandler, String decoderVariable, @Nullable String fieldNameVariable) {
                boolean first = true;
                for (Path path : paths) {
                    // is this path still a candidate?
                    if (first) {
                        builder.beginControlFlow("if ($L)", possibleTypes.anySet(path.subTypes));
                    } else {
                        builder.nextControlFlow("else if ($L)", possibleTypes.anySet(path.subTypes));
                    }
                    first = false;

                    // check there are no other possibilities
                    Set<DeserializationEntity> otherTypes = paths.stream().filter(p -> p != path).flatMap(p -> p.subTypes.stream()).collect(Collectors.toSet());
                    if (!otherTypes.isEmpty()) {
                        builder.addStatement(
                                "if ($L) throw $N.createDeserializationException(\"Ambiguous property\")",
                                possibleTypes.anySet(otherTypes),
                                decoderVariable
                        );
                    }

                    // narrow the possible types
                    possibleTypes.and(builder, path.subTypes);

                    path.property.deserializeProperty(generatorContext, builder, readProperties, unknownPropertyHandler, decoderVariable, fieldNameVariable);
                }
                builder.nextControlFlow("else");
                if (paths.stream().mapToInt(p -> p.subTypes.size()).sum() == subTypes.size()) {
                    // no types left
                    builder.addStatement("throw new AssertionError(\"bad type\")");
                } else {
                    unknownPropertyHandler.deserializeProperty(generatorContext, builder, readProperties, unknownPropertyHandler, decoderVariable, fieldNameVariable);
                }
                builder.endControlFlow();
            }
        }

        private static class Path {
            final Collection<DeserializationEntity> subTypes;
            final DeserializationEntity property;

            Path(@NonNull DeserializationEntity subType, @NonNull DeserializationEntity property) {
                this.subTypes = new ArrayList<>();
                this.subTypes.add(subType);
                this.property = property;
            }

            Path(Collection<DeserializationEntity> subTypes, DeserializationEntity property) {
                this.subTypes = subTypes;
                this.property = property;
            }

            boolean mergeFrom(Path other) {
                if (property.isStructurallyIdentical(other.property)) {
                    subTypes.addAll(other.subTypes);

                    for (DeserializationEntity subType : other.subTypes) {
                        // replace occurences of other.property with our property
                        subType.updateChildren(ent -> {
                            if (ent == other.property) {
                                return property;
                            } else {
                                return ent;
                            }
                        });
                    }
                    return true;
                } else {
                    return false;
                }
            }
        }

        private class TypeTagProperty extends DeserializationEntity {
            private final Map<DeserializationEntity, Collection<String>> tags;

            TypeTagProperty(Map<DeserializationEntity, Collection<String>> tags) {
                this.tags = tags;

                presentAsJava = false;
                hasProperties = false;
            }

            @Override
            void deserializeImpl(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, DeserializationEntity unknownPropertyHandler, String decoderVariable, @Nullable String fieldNameVariable) {
                builder.beginControlFlow("switch ($N.decodeString())", decoderVariable);

                for (Map.Entry<DeserializationEntity, Collection<String>> entry : tags.entrySet()) {
                    for (String alias : entry.getValue()) {
                        builder.add("case $S:\n", alias);
                    }
                    builder.indent();
                    possibleTypes.and(builder, Collections.singleton(entry.getKey()));
                    builder.addStatement("break");
                    builder.unindent();
                }

                builder.add("default:\n");
                builder.indent();
                builder.addStatement("throw $N.createDeserializationException(\"Unknown type tag\")", decoderVariable);
                builder.unindent();
                builder.endControlFlow();
            }
        }
    }

    private static class IgnoreUnknownProperties extends DeserializationEntity {
        @Override
        void deserializeImpl(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, DeserializationEntity unknownPropertyHandler, String decoderVariable, @Nullable String fieldNameVariable) {
            builder.addStatement("$N.skipValue()", decoderVariable);
        }

        @Override
        boolean isStructurallyIdentical(DeserializationEntity other) {
            return other instanceof IgnoreUnknownProperties;
        }
    }

    private static class FailUnknownProperties extends DeserializationEntity {
        @Nullable
        private final GeneratorType typeForErrorMessage;

        FailUnknownProperties(@Nullable GeneratorType typeForErrorMessage) {
            this.typeForErrorMessage = typeForErrorMessage;
        }

        @Override
        void deserializeImpl(GeneratorContext generatorContext, CodeBlock.Builder builder, InlineBitSet<DeserializationEntity> readProperties, DeserializationEntity unknownPropertyHandler, String decoderVariable, @Nullable String fieldNameVariable) {
            String msg;
            if (typeForErrorMessage != null) {
                msg = "Unknown property for type " + typeForErrorMessage.getTypeName() + ": ";
            } else {
                msg = "Unknown property: ";
            }
            // todo: do we really want to output a potentially attacker-controlled field name to the logs here?
            builder.addStatement("throw $N.createDeserializationException($S + $N)", decoderVariable, msg, fieldNameVariable);
        }

        @Override
        boolean isStructurallyIdentical(DeserializationEntity other) {
            return other instanceof FailUnknownProperties;
        }
    }

    private enum UnknownPropertyHandlingStage {
        ANY_SETTER(true),
        IGNORE(false),
        FAIL(true);

        /**
         * Whether different handlers of this stage are distinct, and there should be an error on conflict
         */
        final boolean distinct;

        UnknownPropertyHandlingStage(boolean distinct) {
            this.distinct = distinct;
        }
    }
}
