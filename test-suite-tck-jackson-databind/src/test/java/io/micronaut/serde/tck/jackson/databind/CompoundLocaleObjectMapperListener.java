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
package io.micronaut.serde.tck.jackson.databind;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;

/**
 * Applies Micronaut's compound locale parsing to Jackson Databind test mappers.
 *
 * <p>The shared JsonFormat TCK includes Jackson Databind format scenarios for
 * {@link JsonFormat#locale()}, such as {@code de_DE}, {@code de-DE}, and
 * {@code it_IT_POSIX}. The Jackson version used by this test suite does not
 * yet parse all of those compound locale strings from annotations in the same
 * way, so the Databind baseline would fail before Micronaut Serialization is
 * compared. This listener keeps the Databind mapper aligned with the upstream
 * Jackson format tests while the TCK validates Micronaut's behavior against it.
 */
@Internal
@Singleton
final class CompoundLocaleObjectMapperListener implements BeanCreatedEventListener<ObjectMapper> {

    @Override
    public ObjectMapper onCreated(BeanCreatedEvent<ObjectMapper> event) {
        return event.getBean()
            .rebuild()
            .annotationIntrospector(new CompoundLocaleAnnotationIntrospector())
            .build();
    }

    private static final class CompoundLocaleAnnotationIntrospector extends JacksonAnnotationIntrospector {

        @Override
        public JsonFormat.Value findFormat(MapperConfig<?> config, Annotated ann) {
            JsonFormat.Value value = super.findFormat(config, ann);
            JsonFormat format = ann.getAnnotation(JsonFormat.class);
            if (format == null || JsonFormat.DEFAULT_LOCALE.equals(format.locale())) {
                return value;
            }
            return value.withLocale(StringUtils.parseLocale(format.locale()));
        }
    }
}
