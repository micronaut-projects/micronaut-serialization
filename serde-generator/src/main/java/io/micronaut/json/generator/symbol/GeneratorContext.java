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

import com.squareup.javapoet.*;
import io.micronaut.core.annotation.Internal;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Serializer;
import jakarta.inject.Provider;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Internal
public final class GeneratorContext {
    private final ProblemReporter problemReporter;

    /**
     * A readable path to this context, used for better error messages.
     */
    private final String readablePath;

    private final NameAllocator fields;
    private final NameAllocator localVariables;

    private final Map<InjectableSerializerType, Injected> injectedNormalSerializers;
    private final Map<TypeName, Injected> injectedBeans;

    private GeneratorContext(
            ProblemReporter problemReporter, String readablePath,
            NameAllocator fields,
            NameAllocator localVariables,
            Map<InjectableSerializerType, Injected> injectedNormalSerializers,
            Map<TypeName, Injected> injectedBeans) {
        this.problemReporter = problemReporter;
        this.readablePath = readablePath;
        this.fields = fields;
        this.localVariables = localVariables;
        this.injectedNormalSerializers = injectedNormalSerializers;
        this.injectedBeans = injectedBeans;
    }

    static GeneratorContext create(ProblemReporter problemReporter, String rootReadablePath) {
        return new GeneratorContext(problemReporter, rootReadablePath, new NameAllocator(), null, new HashMap<>(), new HashMap<>());
    }

    public String getReadablePath() {
        return readablePath;
    }

    public GeneratorContext withSubPath(String element) {
        // the other variables are mutable, so we can just reuse them
        return new GeneratorContext(problemReporter, readablePath + "->" + element, fields, localVariables, injectedNormalSerializers, injectedBeans);
    }

    public GeneratorContext newMethodContext(String... usedLocals) {
        if (this.localVariables != null) {
            throw new IllegalStateException("Nesting of local variable scopes not supported");
        }
        NameAllocator localVariables = new NameAllocator();
        for (String usedLocal : usedLocals) {
            String actual = localVariables.newName(usedLocal);
            // usually, newName will return the same name, unless there's a collision or something invalid.
            if (!actual.equals(usedLocal)) {
                throw new IllegalArgumentException("Duplicate or illegal local variable name: " + usedLocal);
            }
        }
        return new GeneratorContext(problemReporter, readablePath, fields, localVariables, injectedNormalSerializers, injectedBeans);
    }

    /**
     * Create a new unique variable, with a name similar to the given {@code nameHint}.
     *
     * @param nameHint a readable name for this variable. Not necessarily a valid java identifier, or unique
     * @return The unique generated variable name
     */
    public String newLocalVariable(String nameHint) {
        return localVariables.newName(nameHint);
    }

    public Injected requestInjection(InjectableSerializerType injectable) {
        return injectedNormalSerializers.computeIfAbsent(injectable, t -> {
            String fieldName = fields.newName(t.fieldType.toString());
            return new Injected(fieldName);
        });
    }

    public Injected requestInjection(TypeName beanType) {
        return injectedBeans.computeIfAbsent(beanType, t -> {
            String fieldName = fields.newName(beanType.toString());
            return new Injected(fieldName);
        });
    }

    Map<InjectableSerializerType, Injected> getInjectedNormalSerializers() {
        return injectedNormalSerializers;
    }

    Map<TypeName, Injected> getInjectedBeans() {
        return injectedBeans;
    }

    public ProblemReporter getProblemReporter() {
        return problemReporter;
    }

    public static final class Injected {
        final String fieldName;

        private final CodeBlock accessExpression;

        private Injected(String fieldName) {
            this.fieldName = fieldName;
            this.accessExpression = CodeBlock.of("this.$N", fieldName);
        }

        public CodeBlock getAccessExpression() {
            return accessExpression;
        }
    }

    public static final class InjectableSerializerType {
        final GeneratorType type;
        final TypeName poetType;
        final boolean provider;
        final boolean forSerialization;

        final TypeName fieldType;

        public InjectableSerializerType(GeneratorType type, boolean provider, boolean forSerialization) {
            this.type = type;
            this.provider = provider;
            this.forSerialization = forSerialization;
            this.poetType = type.toPoetName();
            this.fieldType = fieldType(poetType, provider, forSerialization);
        }

        private static TypeName fieldType(TypeName type, boolean provider, boolean forSerialization) {
            ParameterizedTypeName serType;
            if (forSerialization) {
                serType = ParameterizedTypeName.get(ClassName.get(Serializer.class), WildcardTypeName.supertypeOf(type));
            } else {
                serType = ParameterizedTypeName.get(ClassName.get(Deserializer.class), WildcardTypeName.subtypeOf(type));
            }
            if (provider) {
                serType = ParameterizedTypeName.get(ClassName.get(Provider.class), WildcardTypeName.subtypeOf(serType));
            }
            return serType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            InjectableSerializerType that = (InjectableSerializerType) o;
            return provider == that.provider && forSerialization == that.forSerialization && Objects.equals(poetType, that.poetType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(poetType, provider, forSerialization);
        }
    }
}
