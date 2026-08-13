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
package io.micronaut.serde.cbor.body;

import io.micronaut.context.annotation.BootstrapContextCompatible;
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
import io.micronaut.serde.cbor.CborMediaTypes;
import io.micronaut.serde.cbor.CborObjectMapper;
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
 * HTTP message body handler for CBOR using Micronaut Serialization.
 *
 * <p>Object mapping is performed by {@link CborObjectMapper} (build-time serdes + streaming CBOR
 * tokens). Jackson Databind is not used.</p>
 *
 * @param <T> The type to read/write
 * @since 3.1.0
 */
@Order(CborMessageHandler.ORDER)
@Singleton
@CborMessageHandler.ProducesCbor
@CborMessageHandler.ConsumesCbor
@BootstrapContextCompatible
public final class CborMessageHandler<T> implements MessageBodyHandler<T>, ResponseBodyWriter<T> {

    /**
     * Prefer JSON handlers for generic negotiation; CBOR is selected by media type.
     */
    public static final int ORDER = 10;

    private final CborObjectMapper cborObjectMapper;

    /**
     * Creates a handler that delegates CBOR mapping to the given mapper.
     *
     * @param cborObjectMapper The CBOR object mapper
     */
    public CborMessageHandler(CborObjectMapper cborObjectMapper) {
        this.cborObjectMapper = cborObjectMapper;
    }

    /**
     * Returns the CBOR mapper used by this handler.
     *
     * @return The mapper
     */
    public CborObjectMapper getCborObjectMapper() {
        return cborObjectMapper;
    }

    @Override
    public boolean isReadable(Argument<T> type, @Nullable MediaType mediaType) {
        return isCbor(mediaType);
    }

    @Override
    public boolean isWriteable(Argument<T> type, @Nullable MediaType mediaType) {
        return isCbor(mediaType);
    }

    private static boolean isCbor(@Nullable MediaType mediaType) {
        return mediaType != null && mediaType.matchesAllOrWildcardOrExtension(CborMediaTypes.EXTENSION_CBOR);
    }

    private static CodecException decorateRead(Argument<?> type, IOException e) {
        return new CodecException("Error decoding CBOR stream for type [" + type.getName() + "]: " + e.getMessage(), e);
    }

    private static CodecException decorateWrite(Object object, IOException e) {
        return new CodecException("Error encoding object [" + object + "] to CBOR: " + e.getMessage(), e);
    }

    @Override
    public CborMessageHandler<T> createSpecific(Argument<T> type) {
        return new CborMessageHandler<>((CborObjectMapper) cborObjectMapper.createSpecific(type));
    }

    @Override
    public @Nullable T read(Argument<T> type,
                            @Nullable MediaType mediaType,
                            Headers httpHeaders,
                            ByteBuffer<?> byteBuffer) throws CodecException {
        try {
            return cborObjectMapper.readValue(byteBuffer, type);
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
            return cborObjectMapper.readValue(inputStream, type);
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
        outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType != null ? mediaType : CborMediaTypes.APPLICATION_CBOR_TYPE);
        try {
            cborObjectMapper.writeValue(outputStream, type, object);
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
            return bodyFactory.buffer(s -> cborObjectMapper.writeValue(s, type, object));
        } catch (IOException e) {
            throw decorateWrite(object, e);
        }
    }

    /**
     * A {@link Produces} annotation for CBOR.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Produces(CborMediaTypes.APPLICATION_CBOR)
    public @interface ProducesCbor {
    }

    /**
     * A {@link Consumes} annotation for CBOR.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Consumes(CborMediaTypes.APPLICATION_CBOR)
    public @interface ConsumesCbor {
    }
}
