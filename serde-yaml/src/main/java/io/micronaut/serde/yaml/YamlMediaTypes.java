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
package io.micronaut.serde.yaml;

import io.micronaut.http.MediaType;
import org.jspecify.annotations.Nullable;

/**
 * Media type constants for YAML.
 *
 * <p>{@code application/yaml} is the media type registered by RFC 9512; {@code application/x-yaml}
 * is the older type that Micronaut exposes as {@link MediaType#APPLICATION_YAML}. The YAML
 * message body handler accepts both.</p>
 *
 * @since 3.2.0
 */
public final class YamlMediaTypes {

    /**
     * YAML media type string ({@code application/yaml}, RFC 9512).
     */
    public static final String APPLICATION_YAML = "application/yaml";

    /**
     * YAML {@link MediaType} ({@code application/yaml}).
     */
    public static final MediaType APPLICATION_YAML_TYPE = new MediaType(APPLICATION_YAML, "yaml");

    /**
     * Legacy YAML media type string ({@code application/x-yaml}).
     */
    public static final String APPLICATION_X_YAML = "application/x-yaml";

    /**
     * Legacy YAML {@link MediaType} ({@code application/x-yaml}).
     */
    public static final MediaType APPLICATION_X_YAML_TYPE = new MediaType(APPLICATION_X_YAML, "yml");

    private YamlMediaTypes() {
    }

    /**
     * Whether the media type is one of the YAML media types, or a wildcard that covers them.
     *
     * @param mediaType The media type
     * @return {@code true} if YAML can be read from or written to the media type
     */
    public static boolean isYaml(@Nullable MediaType mediaType) {
        return mediaType != null
            && (mediaType.matches(APPLICATION_YAML_TYPE) || mediaType.matches(APPLICATION_X_YAML_TYPE));
    }
}
