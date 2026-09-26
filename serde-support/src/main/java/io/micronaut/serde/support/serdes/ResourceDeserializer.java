/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.support.serdes;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.hateoas.GenericResource;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Reads {@link Resource}-typed values, such as the {@code _embedded} resources of a resource, as
 * {@link GenericResource}, like the delegating {@code @JsonCreator} of {@link Resource} does with
 * jackson-databind.
 *
 * @since 3.2.1
 */
@Internal
@Singleton
@Secondary
@Requires(classes = GenericResource.class)
public final class ResourceDeserializer implements Deserializer<Resource> {

    private static final Argument<GenericResource> GENERIC_RESOURCE = Argument.of(GenericResource.class);

    @Override
    public Resource deserialize(Decoder decoder, DecoderContext context, Argument<? super Resource> type) throws IOException {
        return context.findDeserializer(GENERIC_RESOURCE)
            .createSpecific(context, GENERIC_RESOURCE)
            .deserialize(decoder, context, GENERIC_RESOURCE);
    }
}
