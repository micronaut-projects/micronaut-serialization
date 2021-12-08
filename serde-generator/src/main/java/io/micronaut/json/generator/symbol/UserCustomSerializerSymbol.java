package io.micronaut.json.generator.symbol;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;

@Internal
public final class UserCustomSerializerSymbol extends InjectingSerializerSymbol {
    @Nullable
    private final ClassElement serializerType;
    @Nullable
    private final ClassElement deserializerType;

    public UserCustomSerializerSymbol(ClassElement serializerType, ClassElement deserializerType) {
        this(serializerType, deserializerType, false);
    }

    private UserCustomSerializerSymbol(ClassElement serializerType, ClassElement deserializerType, boolean provider) {
        super(provider);
        this.serializerType = serializerType;
        this.deserializerType = deserializerType;
    }

    @Override
    public SerializerSymbol withRecursiveSerialization() {
        return new UserCustomSerializerSymbol(serializerType, deserializerType, true);
    }

    @Override
    public boolean canSerialize(GeneratorType type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, GeneratorType type) {
    }

    @Override
    protected GeneratorContext.Injected inject(GeneratorContext generatorContext, GeneratorType type, boolean forSerialization) {
        ClassElement requestType = forSerialization ? serializerType : deserializerType;
        if (requestType == null) {
            throw new UnsupportedOperationException();
        }
        TypeName typeName = PoetUtil.toTypeName(requestType);
        if (provider) {
            typeName = ParameterizedTypeName.get(ClassName.get(BeanProvider.class), typeName);
        }
        return generatorContext.requestInjection(typeName);
    }
}
