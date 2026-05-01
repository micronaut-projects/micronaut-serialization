/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.serde.support.deserializers.collect;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.type.Argument;
import io.micronaut.json.convert.JsonNodeConvertibleValues;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.DeserializerRegistrar;

import java.io.IOException;
import java.util.Optional;

@Internal
@SuppressWarnings("rawtypes")
final class ConvertibleValuesDeserializer implements DeserializerRegistrar<ConvertibleValues> {
    private final ConversionService conversionService;

    ConvertibleValuesDeserializer(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Deserializer<ConvertibleValues> createSpecific(DecoderContext context, Argument<? super ConvertibleValues> type) throws SerdeException {
        Optional<Argument<?>> var = type.getFirstTypeVariable();
        if (var.isPresent()) {
            //noinspection unchecked
            return new Specialized((Argument<Object>) var.get(), (Deserializer<Object>) context.findDeserializer(var.get()));
        } else {
            return this;
        }
    }

    @Override
    public ConvertibleValues deserialize(Decoder decoder, DecoderContext context, Argument<? super ConvertibleValues> type) throws IOException {
        JsonNode node = decoder.decodeNode();
        if (!node.isObject()) {
            throw decoder.createDeserializationException("Expected object", node);
        }
        return new JsonNodeConvertibleValues(node, conversionService);
    }

    @Override
    public Argument<ConvertibleValues> getType() {
        return Argument.of(ConvertibleValues.class, Argument.ofTypeVariable(Object.class, "V"));
    }

    private class Specialized implements Deserializer<ConvertibleValues> {
        private final Argument<Object> componentType;
        private final Deserializer<Object> componentDeserializer;

        Specialized(Argument<Object> componentType, Deserializer<Object> componentDeserializer) {
            this.componentType = componentType;
            this.componentDeserializer = componentDeserializer;
        }

        @Override
        public ConvertibleValues deserialize(Decoder decoder, DecoderContext context, Argument<? super ConvertibleValues> type) throws IOException {
            Decoder obj = decoder.decodeObject(type);
            MutableConvertibleValuesMap map = new MutableConvertibleValuesMap();
            map.setConversionService(conversionService);
            while (true) {
                String key = obj.decodeKey();
                if (key == null) {
                    break;
                }
                map.put(key, componentDeserializer.deserialize(obj, context, componentType));
            }
            obj.finishStructure();
            return map;
        }
    }
}
