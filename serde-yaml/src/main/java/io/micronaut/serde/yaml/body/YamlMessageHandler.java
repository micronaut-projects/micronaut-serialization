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
package io.micronaut.serde.yaml.body;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.serde.yaml.YamlMediaTypes;
import io.micronaut.serde.yaml.YamlObjectMapper;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * HTTP message body handler for YAML using Micronaut Serialization.
 *
 * <p>Handles {@code application/yaml} and {@code application/x-yaml} request and response bodies
 * with the {@link YamlObjectMapper}. The handler is only loaded when Micronaut HTTP is on the
 * classpath.</p>
 *
 * @param <T> The type to read/write
 * @since 3.2.0
 */
@Order(YamlMessageHandler.ORDER)
@Singleton
@Requires(classes = MessageBodyHandler.class)
@YamlMessageHandler.ProducesYaml
@YamlMessageHandler.ConsumesYaml
public final class YamlMessageHandler<T> implements MessageBodyHandler<T>, ResponseBodyWriter<T> {

    /**
     * Prefer JSON handlers for generic negotiation; YAML is selected by media type.
     */
    public static final int ORDER = 10;

    private final YamlObjectMapper yamlObjectMapper;

    /**
     * Creates a handler that delegates YAML mapping to the given mapper.
     *
     * @param yamlObjectMapper The YAML object mapper
     */
    public YamlMessageHandler(YamlObjectMapper yamlObjectMapper) {
        this.yamlObjectMapper = yamlObjectMapper;
    }

    /**
     * Returns the YAML mapper used by this handler.
     *
     * @return The mapper
     */
    public YamlObjectMapper getYamlObjectMapper() {
        return yamlObjectMapper;
    }

    @Override
    public boolean isReadable(Argument<T> type, @Nullable MediaType mediaType) {
        return YamlMediaTypes.isYaml(mediaType);
    }

    @Override
    public boolean isWriteable(Argument<T> type, @Nullable MediaType mediaType) {
        return YamlMediaTypes.isYaml(mediaType);
    }

    private static CodecException decorateRead(Argument<?> type, IOException e) {
        return new CodecException("Error decoding YAML stream for type [" + type.getName() + "]: " + e.getMessage(), e);
    }

    private static CodecException decorateWrite(Object object, IOException e) {
        return new CodecException("Error encoding object [" + object + "] to YAML: " + e.getMessage(), e);
    }

    @Override
    public @Nullable T read(Argument<T> type,
                            @Nullable MediaType mediaType,
                            Headers httpHeaders,
                            ByteBuffer<?> byteBuffer) throws CodecException {
        try {
            return yamlObjectMapper.readValue(byteBuffer, type);
        } catch (IOException e) {
            throw decorateRead(type, e);
        } finally {
            if (byteBuffer instanceof ReferenceCounted rc) {
                rc.release();
            }
        }
    }

    @Override
    public @Nullable T read(Argument<T> type,
                            @Nullable MediaType mediaType,
                            Headers httpHeaders,
                            InputStream inputStream) throws CodecException {
        try {
            return yamlObjectMapper.readValue(inputStream, type);
        } catch (IOException e) {
            throw decorateRead(type, e);
        }
    }

    @Override
    public void writeTo(Argument<T> type,
                        @Nullable MediaType mediaType,
                        T object,
                        MutableHeaders outgoingHeaders,
                        OutputStream outputStream) throws CodecException {
        outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType != null ? mediaType : YamlMediaTypes.APPLICATION_YAML_TYPE);
        try {
            yamlObjectMapper.writeValue(outputStream, type, object);
        } catch (IOException e) {
            throw decorateWrite(object, e);
        }
    }

    @Override
    public CloseableByteBody writePiece(ByteBodyFactory bodyFactory,
                                        HttpRequest<?> request,
                                        HttpResponse<?> response,
                                        Argument<T> type,
                                        MediaType mediaType,
                                        T object) throws CodecException {
        try {
            return bodyFactory.buffer(s -> yamlObjectMapper.writeValue(s, type, object));
        } catch (IOException e) {
            throw decorateWrite(object, e);
        }
    }

    /**
     * A {@link Produces} annotation for YAML.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Produces({YamlMediaTypes.APPLICATION_YAML, YamlMediaTypes.APPLICATION_X_YAML})
    public @interface ProducesYaml {
    }

    /**
     * A {@link Consumes} annotation for YAML.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Consumes({YamlMediaTypes.APPLICATION_YAML, YamlMediaTypes.APPLICATION_X_YAML})
    public @interface ConsumesYaml {
    }
}
