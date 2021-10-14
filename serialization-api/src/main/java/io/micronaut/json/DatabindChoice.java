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
package io.micronaut.json;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Internal
public class DatabindChoice {
    private static final String PROPERTY_KEY = "micronaut.databind";
    private static final String PROPERTY_VALUE = "generated";

    private static final boolean ENABLED;

    static {
        boolean jacksonOnClasspath;
        try {
            Class.forName("io.micronaut.jackson.ObjectMapperFactory");
            jacksonOnClasspath = true;
        } catch (ClassNotFoundException ignored) {
            jacksonOnClasspath = false;
        }

        String prop = System.getProperty(PROPERTY_KEY);
        if (prop != null) {
            if (prop.equals(PROPERTY_VALUE)) {
                ENABLED = true;
            } else if (prop.equals("jackson")) {
                ENABLED = false;
                if (!jacksonOnClasspath) {
                    throw new IllegalStateException("Jackson not on classpath, but explicitly requested in system property " + PROPERTY_KEY);
                }
            } else {
                throw new IllegalStateException("Invalid value for system property " + PROPERTY_KEY);
            }
        } else {
            ENABLED = !jacksonOnClasspath;
        }
    }

    public static boolean isGeneratedDatabindEnabled() {
        return ENABLED;
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Requires(property = PROPERTY_KEY, notEquals = PROPERTY_VALUE)
    public @interface RequiresJackson {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Requires(property = PROPERTY_KEY, value = PROPERTY_VALUE)
    public @interface RequiresGenerator {
    }
}
