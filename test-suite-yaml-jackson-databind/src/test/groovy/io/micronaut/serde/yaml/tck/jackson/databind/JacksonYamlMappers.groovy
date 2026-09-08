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
package io.micronaut.serde.yaml.tck.jackson.databind

import io.micronaut.core.type.Argument
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.common.FlowStyle
import tools.jackson.databind.JavaType
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.dataformat.yaml.YAMLReadFeature
import tools.jackson.dataformat.yaml.YAMLSchema
import tools.jackson.dataformat.yaml.YAMLWriteFeature

/**
 * Builds Jackson Databind YAML mappers from the canonical Micronaut configuration keys the TCK
 * uses, so the same feature tests run against both implementations.
 *
 * <p>Keys with no Jackson counterpart are ignored, which keeps the TCK robust as features are
 * added on either side.</p>
 */
final class JacksonYamlMappers {

    static final String READ = 'micronaut.serde.format.yaml.read-features.'
    static final String WRITE = 'micronaut.serde.format.yaml.write-features.'

    private JacksonYamlMappers() {
    }

    static YAMLMapper mapper(Map<String, Object> properties) {
        // The TCK asserts on documents without a start marker and with quotes minimized,
        // which is what the Micronaut YAML mapper writes by default.
        def factory = YAMLFactory.builder()
                .yamlSchema(YAMLSchema.CORE)
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .enable(YAMLWriteFeature.MINIMIZE_QUOTES)

        configure(factory, properties, READ + 'empty-string-as-null', YAMLReadFeature.EMPTY_STRING_AS_NULL, true)

        configure(factory, properties, WRITE + 'minimize-quotes', YAMLWriteFeature.MINIMIZE_QUOTES)
        configure(factory, properties, WRITE + 'literal-block-style', YAMLWriteFeature.LITERAL_BLOCK_STYLE)
        configure(factory, properties, WRITE + 'split-lines', YAMLWriteFeature.SPLIT_LINES)
        configure(factory, properties, WRITE + 'canonical-output', YAMLWriteFeature.CANONICAL_OUTPUT)
        configure(factory, properties, WRITE + 'indent-arrays', YAMLWriteFeature.INDENT_ARRAYS)
        configure(factory, properties, WRITE + 'indent-arrays-with-indicator', YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
        configure(factory, properties, WRITE + 'allow-long-keys', YAMLWriteFeature.ALLOW_LONG_KEYS)
        configure(factory, properties, WRITE + 'use-yaml-nonfinite-notation', YAMLWriteFeature.USE_YAML_NONFINITE_NOTATION)
        configure(factory, properties, WRITE + 'explicit-start', YAMLWriteFeature.WRITE_DOC_START_MARKER)

        def dumpSettings = DumpSettings.builder()
        boolean hasDumpSettings = false
        if (properties.containsKey(WRITE + 'write-style')) {
            dumpSettings.setDefaultFlowStyle(FlowStyle.valueOf(String.valueOf(properties[WRITE + 'write-style'])))
            hasDumpSettings = true
        }
        if (properties.containsKey(WRITE + 'explicit-end')) {
            dumpSettings.setExplicitEnd(booleanValue(properties[WRITE + 'explicit-end']))
            hasDumpSettings = true
        }
        if (properties.containsKey(WRITE + 'indent')) {
            dumpSettings.setIndent(Integer.valueOf(String.valueOf(properties[WRITE + 'indent'])))
            hasDumpSettings = true
        }
        if (hasDumpSettings) {
            factory.dumperOptions(dumpSettings.build())
        }
        YAMLMapper.builder(factory.build()).build()
    }

    /**
     * Jackson writes the document end marker through the emitter only, so the TCK expectation is
     * met by appending it when the configuration asked for one.
     */
    static String withExplicitEnd(Map<String, Object> properties, String yaml) {
        boolean explicitEnd = properties.containsKey(WRITE + 'explicit-end') && booleanValue(properties[WRITE + 'explicit-end'])
        explicitEnd && !yaml.endsWith("...\n") ? yaml + "...\n" : yaml
    }

    private static void configure(def factory, Map<String, Object> properties, String key, def feature, Boolean fallback = null) {
        if (properties.containsKey(key)) {
            factory.configure(feature, booleanValue(properties[key]))
        } else if (fallback != null) {
            factory.configure(feature, fallback)
        }
    }

    private static boolean booleanValue(Object value) {
        value instanceof Boolean ? value : Boolean.valueOf(String.valueOf(value))
    }

    /**
     * Converts a Micronaut {@link Argument}, including its type parameters, to the Jackson type
     * the mapper reads and writes with.
     */
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
