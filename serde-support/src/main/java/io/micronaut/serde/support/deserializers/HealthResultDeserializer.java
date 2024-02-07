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
package io.micronaut.serde.support.deserializers;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.CustomizableDeserializer;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Map;

@Internal
@Singleton
@Requires(classes = HealthResult.class)
final class HealthResultDeserializer implements CustomizableDeserializer<HealthResult> {
    // note: serialization works by default on HealthResult using the normal getters, so we don't need to implement it

    private static final Argument<HealthResultDto> DELEGATE_ARGUMENT = Argument.of(HealthResultDto.class);

    @NonNull
    @Override
    public Deserializer<HealthResult> createSpecific(@NonNull DecoderContext context, @NonNull Argument<? super HealthResult> type) throws SerdeException {
        return new Impl(context.findDeserializer(DELEGATE_ARGUMENT).createSpecific(context, DELEGATE_ARGUMENT));
    }

    private record Impl(
        Deserializer<? extends HealthResultDto> delegate
    ) implements Deserializer<HealthResult> {
        @Nullable
        @Override
        public HealthResult deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super HealthResult> type) throws IOException {
            HealthResultDto dto = delegate.deserialize(decoder, context, DELEGATE_ARGUMENT);
            assert dto != null;
            HealthStatus status = switch (dto.status) {
                case HealthStatus.NAME_DOWN -> HealthStatus.DOWN;
                case HealthStatus.NAME_UP -> HealthStatus.UP;
                default -> new HealthStatus(dto.status);
            };
            return HealthResult.builder(dto.name)
                .status(status)
                .details(dto.details)
                .build();
        }
    }

    @Serdeable
    record HealthResultDto(
        String name,
        String status,
        Map<String, Object> details
    ) {
    }
}
