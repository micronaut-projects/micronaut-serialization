package io.micronaut.json.generated.serializer;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.http.MediaType;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.Function;

final class $io_micronaut_http_MediaType$Serializer implements Serializer<MediaType> {
  private final Serializer<? super OptionalValues<String>> io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalValues_java_lang_String__;

  private final Serializer<? super Charset> io_micronaut_json_Serializer___super_java_nio_charset_Charset_;

  public $io_micronaut_http_MediaType$Serializer(
      Serializer<? super OptionalValues<String>> io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalValues_java_lang_String__,
      Serializer<? super Charset> io_micronaut_json_Serializer___super_java_nio_charset_Charset_) {
    this.io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalValues_java_lang_String__ = io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalValues_java_lang_String__;
    this.io_micronaut_json_Serializer___super_java_nio_charset_Charset_ = io_micronaut_json_Serializer___super_java_nio_charset_Charset_;
  }

  @Override
  public void serialize(Encoder encoder, MediaType value) throws IOException {
    MediaType object = value;
    Encoder encoder_io_micronaut_http_MediaType = encoder.encodeObject();
    String name = object.getName();
    if (name != null && name.length() != 0) {
      encoder_io_micronaut_http_MediaType.encodeKey("name");
      String tmp = name;
      if (tmp == null) {
        encoder_io_micronaut_http_MediaType.encodeNull();
      } else {
        encoder_io_micronaut_http_MediaType.encodeString(tmp);
      }
    }
    String type = object.getType();
    if (type != null && type.length() != 0) {
      encoder_io_micronaut_http_MediaType.encodeKey("type");
      String tmp_ = type;
      if (tmp_ == null) {
        encoder_io_micronaut_http_MediaType.encodeNull();
      } else {
        encoder_io_micronaut_http_MediaType.encodeString(tmp_);
      }
    }
    String subtype = object.getSubtype();
    if (subtype != null && subtype.length() != 0) {
      encoder_io_micronaut_http_MediaType.encodeKey("subtype");
      String tmp__ = subtype;
      if (tmp__ == null) {
        encoder_io_micronaut_http_MediaType.encodeNull();
      } else {
        encoder_io_micronaut_http_MediaType.encodeString(tmp__);
      }
    }
    String extension = object.getExtension();
    if (extension != null && extension.length() != 0) {
      encoder_io_micronaut_http_MediaType.encodeKey("extension");
      String tmp___ = extension;
      if (tmp___ == null) {
        encoder_io_micronaut_http_MediaType.encodeNull();
      } else {
        encoder_io_micronaut_http_MediaType.encodeString(tmp___);
      }
    }
    OptionalValues<String> parameters = object.getParameters();
    if (parameters != null && !this.io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalValues_java_lang_String__.isEmpty(parameters)) {
      encoder_io_micronaut_http_MediaType.encodeKey("parameters");
      OptionalValues<String> tmp____ = parameters;
      if (tmp____ == null) {
        encoder_io_micronaut_http_MediaType.encodeNull();
      } else {
        this.io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalValues_java_lang_String__.serialize(encoder_io_micronaut_http_MediaType, tmp____);
      }
    }
    String quality = object.getQuality();
    if (quality != null && quality.length() != 0) {
      encoder_io_micronaut_http_MediaType.encodeKey("quality");
      String tmp_____ = quality;
      if (tmp_____ == null) {
        encoder_io_micronaut_http_MediaType.encodeNull();
      } else {
        encoder_io_micronaut_http_MediaType.encodeString(tmp_____);
      }
    }
    BigDecimal qualityAsNumber = object.getQualityAsNumber();
    if (qualityAsNumber != null) {
      encoder_io_micronaut_http_MediaType.encodeKey("qualityAsNumber");
      BigDecimal tmp______ = qualityAsNumber;
      if (tmp______ == null) {
        encoder_io_micronaut_http_MediaType.encodeNull();
      } else {
        encoder_io_micronaut_http_MediaType.encodeBigDecimal(tmp______);
      }
    }
    String version = object.getVersion();
    if (version != null && version.length() != 0) {
      encoder_io_micronaut_http_MediaType.encodeKey("version");
      String tmp_______ = version;
      if (tmp_______ == null) {
        encoder_io_micronaut_http_MediaType.encodeNull();
      } else {
        encoder_io_micronaut_http_MediaType.encodeString(tmp_______);
      }
    }
    Optional<Charset> charset = object.getCharset();
    if (charset.isPresent() && !this.io_micronaut_json_Serializer___super_java_nio_charset_Charset_.isEmpty(charset.get())) {
      encoder_io_micronaut_http_MediaType.encodeKey("charset");
      Optional<Charset> tmp________ = charset;
      if (tmp________.isPresent()) {
        this.io_micronaut_json_Serializer___super_java_nio_charset_Charset_.serialize(encoder_io_micronaut_http_MediaType, tmp________.get());
      } else {
        encoder_io_micronaut_http_MediaType.encodeNull();
      }
    }
    encoder_io_micronaut_http_MediaType.encodeKey("textBased");
    encoder_io_micronaut_http_MediaType.encodeBoolean(object.isTextBased());
    encoder_io_micronaut_http_MediaType.finishStructure();
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = MediaType.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(MediaType.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $io_micronaut_http_MediaType$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $io_micronaut_http_MediaType$Serializer(locator.findContravariantSerializer(Argument.of(OptionalValues.class, Argument.of(String.class))), locator.findContravariantSerializer(Argument.of(Charset.class)));
    }
  }
}
