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
package io.micronaut.serde.yaml

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.common.FlowStyle
import tools.jackson.databind.JavaType
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLAnchorReplayingFactory
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.dataformat.yaml.YAMLReadFeature
import tools.jackson.dataformat.yaml.YAMLSchema
import tools.jackson.dataformat.yaml.YAMLWriteFeature

import java.nio.charset.StandardCharsets

abstract class AbstractJacksonDatabindYamlSpec extends AbstractYamlSerializationSpec {

    private YAMLMapper configuredYamlMapper
    private final ObjectMapper anchorReplayingMapper = new ObjectMapper(new YAMLAnchorReplayingFactory())
    private boolean writeExplicitEnd

    abstract YAMLMapper getDatabindYamlMapper()

    @Override
    protected void initializeMapper(ApplicationContext context) {
        def environment = context.environment
        writeExplicitEnd = false
        def factoryBuilder = YAMLFactory.builder()
                .yamlSchema(YAMLSchema.CORE)
                .enable(YAMLReadFeature.EMPTY_STRING_AS_NULL)
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
        def dumpSettings = DumpSettings.builder()
        def hasDumpSettings = false

        def writeStyle = environment.getProperty(
                'micronaut.serde.format.yaml.write-features.write-style',
                String
        )
        if (writeStyle.present) {
            dumpSettings.setDefaultFlowStyle(FlowStyle.valueOf(writeStyle.get()))
            hasDumpSettings = true
        }

        def explicitStart = environment.getProperty(
                'micronaut.serde.format.yaml.write-features.explicit-start',
                Boolean
        )
        if (explicitStart.present) {
            factoryBuilder.configure(YAMLWriteFeature.WRITE_DOC_START_MARKER, explicitStart.get())
        }

        def explicitEnd = environment.getProperty(
                'micronaut.serde.format.yaml.write-features.explicit-end',
                Boolean
        )
        if (explicitEnd.present) {
            dumpSettings.setExplicitEnd(explicitEnd.get())
            writeExplicitEnd = explicitEnd.get()
            hasDumpSettings = true
        }

        def indent = environment.getProperty(
                'micronaut.serde.format.yaml.write-features.indent',
                Integer
        )
        if (indent.present) {
            dumpSettings.setIndent(indent.get())
            hasDumpSettings = true
        }

        if (hasDumpSettings) {
            factoryBuilder.dumperOptions(dumpSettings.build())
        }
        configuredYamlMapper = YAMLMapper.builder(factoryBuilder.build()).build()
    }

    @Override
    def <T> T readYaml(String yaml, Argument<T> type) {
        activeMapper.readValue(yaml, toJavaType(type))
    }

    @Override
    def <T> T readYaml(byte[] yaml, Argument<T> type) {
        readYaml(new String(yaml, StandardCharsets.UTF_8), type)
    }

    @Override
    def <T> T readYaml(InputStream yaml, Argument<T> type) {
        readYaml(yaml.readAllBytes(), type)
    }

    @Override
    protected <T> T readYamlWithAliases(String yaml, Argument<T> type) {
        anchorReplayingMapper.readValue(yaml, toJavaType(type))
    }

    @Override
    protected <T> T readYamlWithRootWrapper(String yaml, Argument<T> type) {
        activeMapper
                .readerFor(toJavaType(type))
                .withRootName(type.type.simpleName)
                .readValue(yaml)
    }

    @Override
    String writeYaml(Object bean) {
        withExplicitEnd(new String(activeMapper.writeValueAsBytes(bean), StandardCharsets.UTF_8))
    }

    @Override
    String writeYaml(Argument<?> argument, Object bean) {
        withExplicitEnd(new String(activeMapper.writerFor(toJavaType(argument)).writeValueAsBytes(bean), StandardCharsets.UTF_8))
    }

    @Override
    byte[] writeYamlAsBytes(Object bean) {
        return writeYaml(bean).getBytes(StandardCharsets.UTF_8)
    }

    @Override
    byte[] writeYamlAsBytes(Argument<?> argument, Object bean) {
        return writeYaml(argument, bean).getBytes(StandardCharsets.UTF_8)
    }

    @Override
    void writeYaml(OutputStream outputStream, Argument<?> argument, Object bean) {
        outputStream.write(writeYamlAsBytes(argument, bean))
    }

    private JavaType toJavaType(Argument<?> argument) {
        if (!argument.typeParameters) {
            return activeMapper.typeFactory.constructType(argument.type)
        }
        return activeMapper.typeFactory.constructParametricType(
            argument.type,
            argument.typeParameters.collect { toJavaType(it) } as JavaType[]
        )
    }

    private YAMLMapper getActiveMapper() {
        configuredYamlMapper ?: databindYamlMapper
    }

    private String withExplicitEnd(String yaml) {
        writeExplicitEnd && !yaml.endsWith("...\n") ? yaml + "...\n" : yaml
    }

}
