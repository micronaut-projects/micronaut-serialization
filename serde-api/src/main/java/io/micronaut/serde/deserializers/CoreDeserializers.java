package io.micronaut.serde.deserializers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Optional;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

@Factory
public class CoreDeserializers {

    @Singleton
    protected Deserializer<String> stringDeserializer() {
        return (decoder, decoderContext, type) -> decoder.decodeString();
    }

    @Singleton
    @Order(-100) // prioritize over hashset
    protected <E> Deserializer<ArrayList<E>> arrayListDeserializer() {
        return (decoder, decoderContext, type) -> {
            final Argument<?>[] generics = type.getTypeParameters();
            if (ArrayUtils.isEmpty(generics)) {
                throw new SerdeException("Cannot deserialize raw list");
            }
            @SuppressWarnings("unchecked") final Argument<E> generic = (Argument<E>) generics[0];
            final Deserializer<? extends E> valueDeser = decoderContext.findDeserializer(generic);
            final Decoder arrayDecoder = decoder.decodeArray();
            ArrayList<E> list = new ArrayList<>();
            while (arrayDecoder.hasNextArrayValue()) {
                list.add(
                        valueDeser.deserialize(
                                arrayDecoder,
                                decoderContext,
                                generic
                        )
                );
            }
            arrayDecoder.finishStructure();
            return list;
        };
    }

    @Singleton
    protected <E> Deserializer<HashSet<E>> hashSetDeserializer() {
        return (decoder, decoderContext, type) -> {
            final Argument[] generics = type.getTypeParameters();
            if (ArrayUtils.isEmpty(generics)) {
                throw new SerdeException("Cannot deserialize raw list");
            }
            @SuppressWarnings("unchecked") final Argument<E> generic = (Argument<E>) generics[0];
            final Deserializer<? extends E> valueDeser = decoderContext.findDeserializer(generic);
            final Decoder arrayDecoder = decoder.decodeArray();
            HashSet<E> set = new HashSet<>();
            while (arrayDecoder.hasNextArrayValue()) {
                set.add(
                        valueDeser.deserialize(
                                arrayDecoder,
                                decoderContext,
                                generic
                        )
                );
            }
            arrayDecoder.finishStructure();
            return set;
        };
    }

    @Singleton
    protected <V> Deserializer<LinkedHashMap<String, V>> linkedHashMapDeserializer() {
        return (decoder, decoderContext, type) -> {
            final Argument<?>[] generics = type.getTypeParameters();
            if (ArrayUtils.isEmpty(generics) && generics.length != 2) {
                throw new SerdeException("Cannot deserialize raw map");
            }
            @SuppressWarnings("unchecked") final Argument<V> generic = (Argument<V>) generics[1];
            final Deserializer<? extends V> valueDeser = decoderContext.findDeserializer(generic);
            final Decoder objectDecoder = decoder.decodeObject();
            String key = objectDecoder.decodeKey();
            LinkedHashMap<String, V> map = new LinkedHashMap<>();
            while (key != null) {
                map.put(key, valueDeser.deserialize(
                                objectDecoder,
                                decoderContext,
                                generic
                        )
                );
                key = objectDecoder.decodeKey();
            }
            objectDecoder.finishStructure();
            return map;
        };
    }

    @Singleton
    protected <V> Deserializer<Optional<V>> optionalDeserializer() {
        return (decoder, decoderContext, type) -> {
            @SuppressWarnings("unchecked") final Argument<V> generic =
                    (Argument<V>) type.getFirstTypeVariable().orElse(null);

            final Deserializer<? extends V> deserializer = decoderContext.findDeserializer(generic);

            return Optional.ofNullable(
                    deserializer.deserialize(
                            decoder,
                            decoderContext,
                            generic
                    )
            );
        };
    }
}
