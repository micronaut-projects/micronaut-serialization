package io.micronaut.http.hateoas;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Function;

final class $JsonError$Serializer implements Serializer<JsonError> {
  private final Serializer<? super OptionalMultiValues<Resource>> io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalMultiValues_io_micronaut_http_hateoas_Resource__;

  private final Serializer<? super OptionalMultiValues<Link>> io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalMultiValues_io_micronaut_http_hateoas_Link__;

  public $JsonError$Serializer(
      Serializer<? super OptionalMultiValues<Resource>> io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalMultiValues_io_micronaut_http_hateoas_Resource__,
      Serializer<? super OptionalMultiValues<Link>> io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalMultiValues_io_micronaut_http_hateoas_Link__) {
    this.io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalMultiValues_io_micronaut_http_hateoas_Resource__ = io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalMultiValues_io_micronaut_http_hateoas_Resource__;
    this.io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalMultiValues_io_micronaut_http_hateoas_Link__ = io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalMultiValues_io_micronaut_http_hateoas_Link__;
  }

  @Override
  public void serialize(Encoder encoder, JsonError value) throws IOException {
    JsonError object = value;
    Encoder encoder_io_micronaut_http_hateoas_JsonError = encoder.encodeObject();
    String message = object.getMessage();
    if (message != null && message.length() != 0) {
      encoder_io_micronaut_http_hateoas_JsonError.encodeKey("message");
      String tmp = message;
      if (tmp == null) {
        encoder_io_micronaut_http_hateoas_JsonError.encodeNull();
      } else {
        encoder_io_micronaut_http_hateoas_JsonError.encodeString(tmp);
      }
    }
    Optional<String> logref = object.getLogref();
    if (logref.isPresent() && logref.get().length() != 0) {
      encoder_io_micronaut_http_hateoas_JsonError.encodeKey("logref");
      Optional<String> tmp_ = logref;
      if (tmp_.isPresent()) {
        encoder_io_micronaut_http_hateoas_JsonError.encodeString(tmp_.get());
      } else {
        encoder_io_micronaut_http_hateoas_JsonError.encodeNull();
      }
    }
    Optional<String> path = object.getPath();
    if (path.isPresent() && path.get().length() != 0) {
      encoder_io_micronaut_http_hateoas_JsonError.encodeKey("path");
      Optional<String> tmp__ = path;
      if (tmp__.isPresent()) {
        encoder_io_micronaut_http_hateoas_JsonError.encodeString(tmp__.get());
      } else {
        encoder_io_micronaut_http_hateoas_JsonError.encodeNull();
      }
    }
    OptionalMultiValues<Link> links = object.getLinks();
    if (links != null && !this.io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalMultiValues_io_micronaut_http_hateoas_Link__.isEmpty(links)) {
      encoder_io_micronaut_http_hateoas_JsonError.encodeKey("links");
      OptionalMultiValues<Link> tmp___ = links;
      if (tmp___ == null) {
        encoder_io_micronaut_http_hateoas_JsonError.encodeNull();
      } else {
        this.io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalMultiValues_io_micronaut_http_hateoas_Link__.serialize(encoder_io_micronaut_http_hateoas_JsonError, tmp___);
      }
    }
    OptionalMultiValues<Resource> embedded = object.getEmbedded();
    if (embedded != null && !this.io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalMultiValues_io_micronaut_http_hateoas_Resource__.isEmpty(embedded)) {
      encoder_io_micronaut_http_hateoas_JsonError.encodeKey("embedded");
      OptionalMultiValues<Resource> tmp____ = embedded;
      if (tmp____ == null) {
        encoder_io_micronaut_http_hateoas_JsonError.encodeNull();
      } else {
        this.io_micronaut_json_Serializer___super_io_micronaut_core_value_OptionalMultiValues_io_micronaut_http_hateoas_Resource__.serialize(encoder_io_micronaut_http_hateoas_JsonError, tmp____);
      }
    }
    encoder_io_micronaut_http_hateoas_JsonError.finishStructure();
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = JsonError.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(JsonError.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $JsonError$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $JsonError$Serializer(locator.findContravariantSerializer(Argument.of(OptionalMultiValues.class, Argument.of(Resource.class))), locator.findContravariantSerializer(Argument.of(OptionalMultiValues.class, Argument.of(Link.class))));
    }
  }
}
