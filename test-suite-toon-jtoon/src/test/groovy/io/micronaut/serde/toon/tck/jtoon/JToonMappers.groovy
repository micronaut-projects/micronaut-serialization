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
package io.micronaut.serde.toon.tck.jtoon

import dev.toonformat.jtoon.DecodeOptions
import dev.toonformat.jtoon.Delimiter
import dev.toonformat.jtoon.EncodeOptions
import dev.toonformat.jtoon.KeyFolding
import dev.toonformat.jtoon.PathExpansion
import io.micronaut.core.type.Argument
import tools.jackson.databind.JavaType
import tools.jackson.databind.ObjectMapper

/**
 * Helper to configure JToon encode/decode options and Jackson types from Micronaut properties.
 */
final class JToonMappers {

    private static final String DELIMITER_PROP = "micronaut.serde.format.toon.delimiter"
    private static final String INDENT_PROP = "micronaut.serde.format.toon.indent"

    private JToonMappers() {
    }

    static EncodeOptions encodeOptions(Map<String, Object> properties) {
        int indent = 2
        Delimiter delimiter = Delimiter.COMMA
        if (properties.containsKey(DELIMITER_PROP)) {
            char d = properties[DELIMITER_PROP].toString().charAt(0)
            delimiter = delimiterFromChar(d)
        }
        if (properties.containsKey(INDENT_PROP)) {
            indent = Integer.parseInt(properties[INDENT_PROP].toString())
        }
        return new EncodeOptions(indent, delimiter, false, KeyFolding.OFF, 0)
    }

    static DecodeOptions decodeOptions(Map<String, Object> properties) {
        int indent = 2
        Delimiter delimiter = Delimiter.COMMA
        if (properties.containsKey(DELIMITER_PROP)) {
            char d = properties[DELIMITER_PROP].toString().charAt(0)
            delimiter = delimiterFromChar(d)
        }
        if (properties.containsKey(INDENT_PROP)) {
            indent = Integer.parseInt(properties[INDENT_PROP].toString())
        }
        return new DecodeOptions(indent, delimiter, false, PathExpansion.SAFE, DecodeOptions.MAX_ALLOWED_DEPTH, DecodeOptions.DEFAULT_MAX_ARRAY_SIZE, DecodeOptions.DEFAULT_MAX_STRING_LENGTH)
    }

    private static Delimiter delimiterFromChar(char d) {
        switch (d) {
            case '\t': return Delimiter.TAB
            case '|': return Delimiter.PIPE
            case ',':
            default: return Delimiter.COMMA
        }
    }

    static JavaType toJavaType(ObjectMapper mapper, Argument<?> argument) {
        if (!argument.typeParameters) {
            return mapper.typeFactory.constructType(argument.type)
        }
        return mapper.typeFactory.constructParametricType(
                argument.type,
                argument.typeParameters.collect { toJavaType(mapper, it) } as JavaType[]
        )
    }
}
