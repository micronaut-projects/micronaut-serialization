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
package io.micronaut.json.generated.serializer;

import io.micronaut.core.type.Argument;
import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerdeRegistry;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;

/**
 * Marker class to tell the generator that we should generate the primitive serializers here.
 */
@GeneratePrimitiveSerializers
class PrimitiveGenerators {
    // serializers for raw types

    @SuppressWarnings("rawtypes")
    private static abstract class Raw<T> implements Serializer<T>, Deserializer<T> {
        private final Serializer implSer;
        private final Deserializer implDes;

        Raw(SerdeRegistry locator, Type genericType) {
            this.implSer = locator.findContravariantSerializer(genericType);
            this.implDes = locator.findInvariantDeserializer(genericType);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(Decoder decoder) throws IOException {
            return (T) implDes.deserialize(decoder);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void serialize(Encoder encoder, T value) throws IOException {
            implSer.serialize(encoder, value);
        }
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawList extends Raw<List> {
        RawList(SerdeRegistry locator) {
            super(locator, Argument.listOf(Object.class));
        }
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawCollection extends Raw<Collection> {
        RawCollection(SerdeRegistry locator) {
            super(locator, Argument.of(Collection.class, Argument.OBJECT_ARGUMENT));
        }
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawSet extends Raw<Set> {
        RawSet(SerdeRegistry locator) {
            super(locator, Argument.setOf(Argument.OBJECT_ARGUMENT));
        }
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawSortedSet extends Raw<SortedSet> {
        RawSortedSet(SerdeRegistry locator) {
            super(locator, Argument.of(SortedSet.class, Argument.OBJECT_ARGUMENT));
        }
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawMap extends Raw<Map> {
        RawMap(SerdeRegistry locator) {
            super(locator, Argument.mapOf(Argument.STRING, Argument.OBJECT_ARGUMENT));
        }
    }

    @Singleton
    static class ObjectMapFactory implements Deserializer.Factory {
        @Override
        public Type getGenericType() {
            return Argument.mapOf(
                    Argument.ofTypeVariable(Object.class, "K"),
                    Argument.ofTypeVariable(Object.class, "V")
            );
        }

        @Override
        public Deserializer<?> newInstance(SerdeRegistry registry, Function<String, Type> getTypeParameter) {
            // find a deserializer for Map<String, V>
            Type actualType = Argument.mapOf(
                    Argument.ofTypeVariable(Object.class, "K"),
                    Argument.of(getTypeParameter.apply("V"))
            );
            return registry.findInvariantDeserializer(actualType);
        }
    }
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@interface GeneratePrimitiveSerializers {
}