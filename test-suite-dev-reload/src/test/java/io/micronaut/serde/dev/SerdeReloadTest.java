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
package io.micronaut.serde.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.dev.tck.ReloadHarness;
import io.micronaut.dev.tck.ReloadTck;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.SerdeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs an application through the development runtime and edits its serializable types: the
 * serdes of the next generation are those of the edited types, and nothing of serialization keeps
 * a retired generation reachable.
 */
class SerdeReloadTest {

    private static final String GENRE = """
        package example;

        @io.micronaut.serde.annotation.Serdeable
        public enum Genre {
            %s
        }
        """;

    @TempDir
    Path project;

    @Test
    void serdesFollowAReloadAndLeaveNoRetiredGenerationReachable() throws Exception {
        try (ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            harness.source("example.Genre", GENRE.formatted("SCIFI, CRIME"));
            harness.source("example.Book", """
                package example;

                @io.micronaut.serde.annotation.Serdeable
                public record Book(String title, Genre genre) {
                }
                """);
            harness.start();
            assertReloaderPresent(harness.context());

            assertEquals("Book[title=Dune, genre=SCIFI]", read(harness.context(), "{\"title\":\"Dune\",\"genre\":\"SCIFI\"}"));
            assertEquals("{\"title\":\"Dune\",\"genre\":\"SCIFI\"}", roundTrip(harness.context(), "{\"title\":\"Dune\",\"genre\":\"SCIFI\"}"));
            ReloadTck.assertFollowsReload(harness, context -> deserializer(context, "example.Book"));

            // a property and a constant are added
            harness.source("example.Genre", GENRE.formatted("SCIFI, CRIME, FANTASY"));
            harness.source("example.Book", """
                package example;

                @io.micronaut.serde.annotation.Serdeable
                public record Book(String title, Genre genre, int pages) {
                }
                """);
            harness.reload();
            assertReloaderPresent(harness.context());

            String json = "{\"title\":\"Dune\",\"genre\":\"FANTASY\",\"pages\":412}";
            assertEquals("Book[title=Dune, genre=FANTASY, pages=412]", read(harness.context(), json));
            assertEquals(json, roundTrip(harness.context(), json));
            ReloadTck.assertFollowsReload(harness, context -> deserializer(context, "example.Book"));
            ReloadTck.assertFollowsReload(harness, context -> deserializer(context, "example.Genre"));
            ReloadTck.assertFollowsReload(harness, context -> serializer(context, "example.Book"));

            // nothing of serialization keeps the first generation reachable: the enum lookups of the generated
            // deserializers are kept on the enum class, and the development-only reloader holds the context only
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    private static void assertReloaderPresent(ApplicationContext context) {
        // the bean that rebuilds the registry exists in development mode only
        assertTrue(context.containsBean(type(context, "io.micronaut.serde.support.DevelopmentSerdeReloader")));
    }

    private static String read(ApplicationContext context, String json) throws IOException {
        return String.valueOf(context.getBean(JsonMapper.class).readValue(json, Argument.of(type(context, "example.Book"))));
    }

    private static String roundTrip(ApplicationContext context, String json) throws IOException {
        JsonMapper mapper = context.getBean(JsonMapper.class);
        Object book = mapper.readValue(json, Argument.of(type(context, "example.Book")));
        return new String(mapper.writeValueAsBytes(book), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Object deserializer(ApplicationContext context, String className) {
        try {
            // the deserializer for the type, as a mapper uses it: a generated serde is created per type
            SerdeRegistry registry = context.getBean(SerdeRegistry.class);
            Argument<Object> type = (Argument<Object>) Argument.of(type(context, className));
            return registry.findDeserializer(type).createSpecific(registry.newDecoderContext(null), type);
        } catch (Exception e) {
            throw new AssertionError("No deserializer for " + className, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object serializer(ApplicationContext context, String className) {
        try {
            SerdeRegistry registry = context.getBean(SerdeRegistry.class);
            Argument<Object> type = (Argument<Object>) Argument.of(type(context, className));
            return registry.findSerializer(type).createSpecific(registry.newEncoderContext(null), type);
        } catch (Exception e) {
            throw new AssertionError("No serializer for " + className, e);
        }
    }

    private static Class<?> type(ApplicationContext context, String className) {
        try {
            return Class.forName(className, true, context.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(className + " is not in the application", e);
        }
    }
}
