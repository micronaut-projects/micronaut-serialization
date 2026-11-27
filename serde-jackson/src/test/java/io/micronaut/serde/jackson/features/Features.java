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
package io.micronaut.serde.jackson.features;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Sets the format features of a bean or of a property by name, for a feature that {@code JsonFormat.Feature} of
 * Jackson has no constant for. {@link FeaturesMapper} maps it to the members that {@code @JsonFormat} is mapped to.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Features {

    /**
     * @return The names of the features to enable
     */
    String[] with() default {};

    /**
     * @return The names of the features to disable
     */
    String[] without() default {};
}
