package io.micronaut.json.generator.symbol.bean;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.json.generator.symbol.GeneratorType;
import io.micronaut.json.generator.symbol.NullableSerializerSymbol;
import io.micronaut.json.generator.symbol.PoetUtil;
import io.micronaut.json.generator.symbol.SerializerLinker;
import io.micronaut.json.generator.symbol.SerializerSymbol;
import io.micronaut.json.generator.symbol.UserCustomSerializerSymbol;

import java.util.Objects;

final class SymbolLookup {
    @NonNull
    private final GeneratorType type;
    private final boolean permitRecursiveSerialization;
    @Nullable
    private final Boolean nullable;

    @Nullable
    private final ClassElement customSerializer;
    @Nullable
    private final ClassElement customDeserializer;

    private SymbolLookup(@NonNull GeneratorType type, boolean permitRecursiveSerialization, @Nullable Boolean nullable, @Nullable ClassElement customSerializer, @Nullable ClassElement customDeserializer) {
        this.type = type;
        this.permitRecursiveSerialization = permitRecursiveSerialization;
        this.nullable = nullable;
        this.customSerializer = customSerializer;
        this.customDeserializer = customDeserializer;
    }

    public static SymbolLookup forProperty(PropWithType prop) {
        return new SymbolLookup(prop.type, prop.property.permitRecursiveSerialization, prop.property.nullable, prop.property.customSerializer, prop.property.customDeserializer);
    }

    public static SymbolLookup forAnySetterValue(GeneratorType valueType) {
        return new SymbolLookup(valueType, false, null, null, null);
    }

    public static SymbolLookup forAnyGetterValue(GeneratorType valueType, boolean permitRecursiveSerialization) {
        return new SymbolLookup(valueType, permitRecursiveSerialization, null, null, null);
    }

    public SymbolLookup withNullable(boolean nullable) {
        return new SymbolLookup(type, permitRecursiveSerialization, nullable, customSerializer, customDeserializer);
    }

    public SerializerSymbol lookup(SerializerLinker linker, boolean forSerialization) {
        SerializerSymbol symbol;
        if (forSerialization && customSerializer != null) {
            symbol = new UserCustomSerializerSymbol(customSerializer, null);
        } else if (!forSerialization && customDeserializer != null) {
            symbol = new UserCustomSerializerSymbol(null, customDeserializer);
        } else {
            symbol = linker.findSymbol(type);
        }
        if (permitRecursiveSerialization) {
            symbol = symbol.withRecursiveSerialization();
        }
        // if no nullity is given, infer nullity from the value null support.
        // most types will be wrapped with NullableSerializerSymbol, but e.g. Optional won't be.
        Boolean nullable = this.nullable;
        if (nullable == null) {
            if (type.isPrimitive() && !type.isArray()) {
                nullable = false;
            } else {
                nullable = !symbol.supportsNullDeserialization();
            }
        }
        if (nullable) {
            symbol = new NullableSerializerSymbol(symbol);
        }
        return symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SymbolLookup that = (SymbolLookup) o;
        return permitRecursiveSerialization == that.permitRecursiveSerialization && type.typeEquals(that.type) && Objects.equals(nullable, that.nullable) && Objects.equals(customSerializer, that.customSerializer) && Objects.equals(customDeserializer, that.customDeserializer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(PoetUtil.toTypeName(type), permitRecursiveSerialization, nullable, customSerializer, customDeserializer);
    }
}
