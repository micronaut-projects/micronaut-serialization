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

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterizedTypeName;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.json.Encoder;
import io.micronaut.json.annotation.SerializableBean;
import io.micronaut.json.generator.symbol.*;

import java.util.*;
import java.util.function.Consumer;

@Internal
public class InlineBeanSerializerSymbol implements SerializerSymbol {
    final SerializerLinker linker;

    @Nullable
    private final AnnotationValue<SerializableBean> fixedAnnotation;

    public InlineBeanSerializerSymbol(SerializerLinker linker) {
        this.linker = linker;
        this.fixedAnnotation = null;
    }

    private InlineBeanSerializerSymbol(InlineBeanSerializerSymbol original, AnnotationValue<SerializableBean> fixedAnnotation) {
        this.linker = original.linker;
        this.fixedAnnotation = fixedAnnotation;
    }

    /**
     * Create a new {@link InlineBeanSerializerSymbol} that uses the given annotation for configuration, instead of the
     * one actually declared on the class.
     */
    public InlineBeanSerializerSymbol withFixedAnnotation(AnnotationValue<SerializableBean> fixedAnnotation) {
        return new InlineBeanSerializerSymbol(this, fixedAnnotation);
    }

    BeanDefinition introspect(ProblemReporter problemReporter, GeneratorType type, boolean forSerialization) {
        return BeanIntrospector.introspect(problemReporter, linker.typeResolutionContext, type.getClassElement(), type.getClassLevelAnnotations(), forSerialization);
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        // can we serialize inline?
        return canSerialize(type, true);
    }

    public boolean canSerializeStandalone(GeneratorType type) {
        return canSerialize(type, false);
    }

    private boolean canSerialize(GeneratorType type, boolean inlineRole) {
        AnnotationValue<SerializableBean> annotation = getAnnotation(type);
        if (annotation == null) {
            return false;
        }
        return annotation.get("inline", Boolean.class).orElse(false) == inlineRole;
    }

    @Nullable
    private AnnotationValue<SerializableBean> getAnnotation(GeneratorType type) {
        if (fixedAnnotation != null) {
            return fixedAnnotation;
        }
        return type.getClassLevelAnnotations().getAnnotation(SerializableBean.class);
    }

    /**
     * Returns {@code false} iff this direction is explicitly disabled (e.g. by
     * {@link SerializableBean#allowDeserialization()}).
     */
    public boolean supportsDirection(GeneratorType type, boolean serialization) {
        AnnotationValue<SerializableBean> annotation = getAnnotation(type);
        if (annotation == null) {
            return true;
        }
        return annotation.booleanValue(serialization ? "allowSerialization" : "allowDeserialization").orElse(true);
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
        if (!visitor.visitStructure()) {
            return;
        }
        // have to check both ser/deser, in case property types differ (e.g. when setters and getters have different types)
        // technically, this could lead to false positives for checking, since ser types will be considered in a subgraph that is only reachable through deser
        for (boolean ser : new boolean[]{true, false}) {
            if (!supportsDirection(type, ser)) {
                continue;
            }

            ProblemReporter problemReporter = new ProblemReporter();
            visitDependencies(visitor, problemReporter, type, ser);
        }
    }

    private void visitDependencies(DependencyVisitor visitor, ProblemReporter problemReporter, GeneratorType type, boolean ser) {
        BeanDefinition definition = introspect(problemReporter, type, ser);
        if (problemReporter.isFailed()) {
            // definition may be in an invalid state. The actual errors will be reported by the codegen, so just skip here
            return;
        }
        if (definition.subtyping != null) {
            for (GeneratorType subType : definition.subtyping.subTypes) {
                visitDependencies(visitor, problemReporter, subType, ser);
            }
            return;
        }
        for (BeanDefinition.Property prop : definition.props) {
            PropWithType propWithType = PropWithType.fromContext(type, prop);
            if (prop.unwrapped) {
                visitDependencies(visitor, problemReporter, propWithType.type, ser);
            } else {
                SerializerSymbol symbol = SymbolLookup.forProperty(propWithType).withNullable(false).lookup(linker, ser);
                visitor.visitStructureElement(symbol, propWithType.type, prop.getElement());
            }
        }
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, String encoderVariable, GeneratorType type, CodeBlock readExpression) {
        if (!supportsDirection(type, true)) {
            throw new UnsupportedOperationException();
        }

        return serializeImpl(generatorContext, encoderVariable, type, readExpression, true, false);
    }

    /**
     * Serialize the given type.
     *
     * @param generatorContext   The generator context
     * @param encoderVariable    Encoder to serialize to
     * @param type               The type to serialize
     * @param readExpression     The expression containing the value to serialize
     * @param copyReadExpression Whether the expression must only be read once, and thus should be copied if used multiple times
     * @param nakedProperties    Whether to serialize the properties of this bean "naked", without surrounding braces
     */
    private CodeBlock serializeImpl(
            GeneratorContext generatorContext,
            String encoderVariable,
            GeneratorType type,
            CodeBlock readExpression,
            boolean copyReadExpression,
            boolean nakedProperties
    ) {
        BeanDefinition definition = introspect(generatorContext.getProblemReporter(), type, true);
        if (generatorContext.getProblemReporter().isFailed()) {
            // definition may be in an invalid state, so just skip codegen
            return CodeBlock.of("");
        }

        if (definition.valueProperty != null) {
            if (nakedProperties) {
                generatorContext.getProblemReporter().fail("Cannot unwrap @JsonValue, but required in this context", type.getClassElement());
                return CodeBlock.of("");
            }

            // @JsonValue
            PropWithType propWithType = PropWithType.fromContext(type, definition.valueProperty);
            return SymbolLookup.forProperty(propWithType).lookup(linker, true).serialize(
                    generatorContext, encoderVariable,
                    propWithType.type,
                    // we don't need a temp variable here, getPropertyAccessExpression only evaluates the read expression once
                    getPropertyAccessExpression(readExpression, definition.valueProperty));
        } else {
            CodeBlock.Builder serialize = CodeBlock.builder();
            if (copyReadExpression) {
                String objectVarName = generatorContext.newLocalVariable("object");
                serialize.addStatement("$T $N = $L", PoetUtil.toTypeName(type), objectVarName, readExpression);
                readExpression = CodeBlock.of("$N", objectVarName);
            }
            // below here, readExpression is safe to read many times
            if (definition.subtyping != null) {
                if (definition.subtyping.deduce) {
                    // just serialize the value directly
                    serializeSubTyping(generatorContext, encoderVariable, serialize, readExpression, definition.subtyping, s -> {
                    }, nakedProperties);
                } else {
                    switch (definition.subtyping.as) {
                        case PROPERTY:
                            String propertyObjectEncoder;
                            if (!nakedProperties) {
                                propertyObjectEncoder = generatorContext.newLocalVariable("subtypeWrapperObject_" + type.getTypeName());
                                serialize.addStatement("$T $N = $N.encodeObject()", Encoder.class, propertyObjectEncoder, encoderVariable);
                            } else {
                                propertyObjectEncoder = encoderVariable;
                            }
                            serializeSubTyping(
                                    generatorContext,
                                    propertyObjectEncoder,
                                    serialize,
                                    readExpression,
                                    definition.subtyping,
                                    tag -> {
                                        serialize.addStatement("$N.encodeKey($S)", propertyObjectEncoder, definition.subtyping.propertyName);
                                        serialize.addStatement("$N.encodeString($S)", propertyObjectEncoder, tag);
                                    },
                                    true
                            );
                            if (!nakedProperties) {
                                serialize.addStatement("$N.finishStructure()", propertyObjectEncoder);
                            }
                            break;
                        case WRAPPER_OBJECT:
                            String wrapperObjectEncoder;
                            if (!nakedProperties) {
                                wrapperObjectEncoder = generatorContext.newLocalVariable("subtypeWrapperObject_" + type.getTypeName());
                                serialize.addStatement("$T $N = $N.encodeObject()", Encoder.class, wrapperObjectEncoder, encoderVariable);
                            } else {
                                wrapperObjectEncoder = encoderVariable;
                            }
                            serializeSubTyping(
                                    generatorContext,
                                    wrapperObjectEncoder,
                                    serialize,
                                    readExpression,
                                    definition.subtyping,
                                    tag -> serialize.addStatement("$N.encodeKey($S)", wrapperObjectEncoder, tag),
                                    false
                            );
                            if (!nakedProperties) {
                                serialize.addStatement("$N.finishStructure()", wrapperObjectEncoder);
                            }
                            break;
                        case WRAPPER_ARRAY:
                            if (nakedProperties) {
                                generatorContext.getProblemReporter().fail("Cannot unwrap WRAPPER_ARRAY, but required in this context", type.getClassElement());
                                return CodeBlock.of("");
                            }
                            String arrayEncoder = generatorContext.newLocalVariable("subtypeWrapperArray_" + type.getTypeName());
                            serialize.addStatement("$T $N = $N.encodeArray()", Encoder.class, arrayEncoder, encoderVariable);
                            serializeSubTyping(
                                    generatorContext,
                                    arrayEncoder,
                                    serialize,
                                    readExpression,
                                    definition.subtyping,
                                    tag -> serialize.addStatement("$N.encodeString($S)", arrayEncoder, tag),
                                    false
                            );
                            serialize.addStatement("$N.finishStructure()", arrayEncoder);
                            break;
                        default:
                            generatorContext.getProblemReporter().fail("Unsupported subtyping strategy " + definition.subtyping.as, type.getClassElement());
                            return CodeBlock.of("");
                    }
                }
            } else {
                String propEncoder;
                if (!nakedProperties) {
                    propEncoder = generatorContext.newLocalVariable("encoder_" + type.getTypeName());
                    serialize.addStatement("$T $N = $N.encodeObject()", Encoder.class, propEncoder, encoderVariable);
                } else {
                    propEncoder = encoderVariable;
                }
                for (BeanDefinition.Property prop : definition.props) {
                    CodeBlock propRead = getPropertyAccessExpression(readExpression, prop);
                    GeneratorContext subGenerator = generatorContext.withSubPath(prop.name);
                    PropWithType propWithType = PropWithType.fromContext(type, prop);

                    ConditionExpression<CodeBlock> shouldIncludeCheck = ConditionExpression.alwaysTrue();

                    shouldIncludeCheck = shouldIncludeCheck.and(checkJsonViewEnabled(prop).bind(propEncoder));

                    SerializerSymbol symbol = null;
                    if (!prop.unwrapped && !prop.anyGetter) {
                        symbol = SymbolLookup.forProperty(propWithType).lookup(linker, true);
                        shouldIncludeCheck = shouldIncludeCheck.and(symbol.shouldIncludeCheck(subGenerator, propWithType.type, prop.valueInclusionPolicy));
                    }

                    if (!shouldIncludeCheck.isAlwaysTrue()) {
                        String tempVariable = generatorContext.newLocalVariable(propWithType.property.name);
                        serialize.addStatement("$T $N = $L", PoetUtil.toTypeName(propWithType.type), tempVariable, propRead);
                        propRead = CodeBlock.of("$N", tempVariable);
                        serialize.beginControlFlow("if ($L)", shouldIncludeCheck.build(propRead));
                    }

                    if (prop.unwrapped) {
                        serialize.add(serializeImpl(subGenerator, propEncoder, propWithType.type, propRead, true, true));
                    } else if (prop.anyGetter) {
                        if (!propWithType.type.isRawTypeEquals(Map.class)) {
                            generatorContext.getProblemReporter().fail("@JsonAnyGetter must be of exact type Map", prop.getElement());
                            continue;
                        }
                        Optional<Map<String, GeneratorType>> typeArgs = propWithType.type.getTypeArgumentsExact(Map.class);
                        if (!typeArgs.isPresent() || !typeArgs.get().get("K").isRawTypeEquals(String.class)) {
                            generatorContext.getProblemReporter().fail("@JsonAnyGetter must have string key", prop.getElement());
                            continue;
                        }
                        GeneratorType valueType = typeArgs.get().get("V");
                        SerializerSymbol valueSerializer = SymbolLookup.forAnyGetterValue(valueType, prop.permitRecursiveSerialization).lookup(linker, true);
                        String entryName = generatorContext.newLocalVariable("anyGetterEntry");
                        serialize.beginControlFlow("for ($T $N : $L.entrySet())",
                                ParameterizedTypeName.get(ClassName.get(Map.Entry.class), ClassName.get(String.class), PoetUtil.toTypeName(valueType)),
                                entryName,
                                propRead
                        );
                        serialize.addStatement("$N.encodeKey($N.getKey())", propEncoder, entryName);
                        serialize.add(valueSerializer.serialize(subGenerator, propEncoder, valueType, CodeBlock.of("$N.getValue()", entryName)));
                        serialize.endControlFlow();
                    } else {
                        assert symbol != null;
                        serialize.addStatement("$N.encodeKey($S)", propEncoder, prop.name);
                        serialize.add(symbol.serialize(subGenerator, propEncoder, propWithType.type, propRead));
                    }

                    if (!shouldIncludeCheck.isAlwaysTrue()) {
                        serialize.endControlFlow();
                    }
                }
                if (!nakedProperties) {
                    serialize.addStatement("$N.finishStructure()", propEncoder);
                }
            }
            return serialize.build();
        }
    }

    ConditionExpression<String> checkJsonViewEnabled(BeanDefinition.Property prop) {
        if (prop.viewClasses != null) {
            return ConditionExpression.of(coderVariable -> {
                CodeBlock.Builder hasViewCheck = CodeBlock.builder();
                hasViewCheck.add("$N.hasView(", coderVariable);
                boolean first = true;
                for (ClassElement viewClass : prop.viewClasses) {
                    if (!first) {
                        hasViewCheck.add(", ");
                    }
                    first = false;
                    hasViewCheck.add("$T.class", PoetUtil.toTypeName(viewClass));
                }
                hasViewCheck.add(")");
                return hasViewCheck.build();
            });
        } else {
            return ConditionExpression.alwaysTrue();
        }
    }

    /**
     * Generate serialization code for multiple subtypes.
     *
     * @param generatorContext The generator context
     * @param encoderVariable  Encoder to serialize to
     * @param builder          The target code builder
     * @param readExpression   The expression to read the object from, will be evaluated multiple times
     * @param subtyping        The subtyping metadata
     * @param writeTag         The function to write the tag
     * @param innerNaked       Whether the properties of the subtype must be written "naked", without surrounding braces
     */
    private void serializeSubTyping(
            GeneratorContext generatorContext,
            String encoderVariable,
            CodeBlock.Builder builder,
            CodeBlock readExpression,
            BeanDefinition.Subtyping subtyping,
            Consumer<String> writeTag,
            boolean innerNaked
    ) {
        boolean first = true;
        for (GeneratorType subType : subtyping.subTypes) {
            if (first) {
                builder.beginControlFlow("if ($L instanceof $T)", readExpression, PoetUtil.toTypeName(subType));
            } else {
                builder.nextControlFlow("else if ($L instanceof $T)", readExpression, PoetUtil.toTypeName(subType));
            }
            first = false;

            String castVariable = generatorContext.newLocalVariable("object_" + subType.getTypeName());
            builder.addStatement("$T $N = ($T) $L", PoetUtil.toTypeName(subType), castVariable, PoetUtil.toTypeName(subType), readExpression);

            if (subtyping.subTypeNames != null) {
                writeTag.accept(subtyping.subTypeNames.get(subType).iterator().next());
            }

            builder.add(serializeImpl(generatorContext, encoderVariable, subType, CodeBlock.of("$N", castVariable), false, innerNaked));
        }
        builder.nextControlFlow("else");
        builder.addStatement("throw new $T(\"Unrecognized subtype: \" + $L.getClass().getName())", IllegalArgumentException.class, readExpression);
        builder.endControlFlow();
    }

    private CodeBlock getPropertyAccessExpression(CodeBlock beanReadExpression, BeanDefinition.Property prop) {
        if (prop.getter != null) {
            return CodeBlock.of("$L.$N()", beanReadExpression, prop.getter.getName());
        } else if (prop.field != null) {
            return CodeBlock.of("$L.$N", beanReadExpression, prop.field.getName());
        } else {
            throw new AssertionError("No accessor, property should have been filtered");
        }
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, String decoderVariable, GeneratorType type, Setter setter) {
        if (!supportsDirection(type, false)) {
            throw new UnsupportedOperationException();
        }

        DeserializationEntity entity = DeserializationEntity.introspect(this, generatorContext, type, ConditionExpression.alwaysTrue());

        // if there were failures, the entity may be in an inconsistent state, so we avoid codegen.
        if (generatorContext.getProblemReporter().isFailed()) {
            return CodeBlock.of("");
        }

        return entity.deserializeTopLevel(generatorContext, decoderVariable, setter);
    }
}
