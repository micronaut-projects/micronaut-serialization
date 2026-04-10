package io.micronaut.serde.yaml;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonViewUtil;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Named("yaml")
@Singleton
@BootstrapContextCompatible
public final class YamlObjectMapper implements ObjectMapper {

    final SerdeRegistry registry;
    @Nullable
    final SerdeConfiguration serdeConfiguration;
    @Nullable
    final Class<?> view;

    @Inject
    public YamlObjectMapper(SerdeRegistry registry, @Nullable SerdeConfiguration serdeConfiguration, @Nullable Class<?> view) {
        this.registry = registry;
        this.serdeConfiguration = serdeConfiguration;
        this.view = view;
    }

    @Override
    public @Nullable <T> T readValueFromTree(@NonNull JsonNode tree, @NonNull Argument<T> type) throws IOException {
        Deserializer.DecoderContext context = registry.newDecoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view));
        final Deserializer<? extends T> deserializer = context.findDeserializer(type).createSpecific(context, type);
        return deserializer.deserialize(
            JsonNodeDecoder.create(tree, limits()),
            context,
            type
        );
    }

    @Override
    public @Nullable <T> T readValue(@NonNull InputStream inputStream, @NonNull Argument<T> type) throws IOException {
        Deserializer.DecoderContext context = registry.newDecoderContext(JsonViewUtil.extractView(serdeConfiguration, type, view));
        final Deserializer<? extends T> deserializer = context.findDeserializer(type).createSpecific(context, type);
        return deserializer.deserialize(
            YamlDecoder.create(inputStream, limits()),
            context,
            type
        );
    }

    @Override
    public @Nullable <T> T readValue(byte @NonNull [] byteArray, @NonNull Argument<T> type) throws IOException {
        return readValue(new ByteArrayInputStream(byteArray), type);
    }

    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        return null;
    }

    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws IOException {
        return null;
    }

    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {

    }

    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object) throws IOException {

    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        return new byte[0];
    }

    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        return new byte[0];
    }

    @Override
    public JsonStreamConfig getStreamConfig() {
        return null;
    }

    private LimitingStream.@NonNull RemainingLimits limits() {
        return serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }

}
