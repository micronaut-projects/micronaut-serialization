package io.micronaut.json.generated.serializer;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.hateoas.Link;
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

final class $io_micronaut_http_hateoas_Link$Serializer implements Serializer<Link> {
  private final Serializer<? super MediaType> io_micronaut_json_Serializer___super_io_micronaut_http_MediaType_;

  public $io_micronaut_http_hateoas_Link$Serializer(
      Serializer<? super MediaType> io_micronaut_json_Serializer___super_io_micronaut_http_MediaType_) {
    this.io_micronaut_json_Serializer___super_io_micronaut_http_MediaType_ = io_micronaut_json_Serializer___super_io_micronaut_http_MediaType_;
  }

  @Override
  public void serialize(Encoder encoder, Link value) throws IOException {
    Link object = value;
    Encoder encoder_io_micronaut_http_hateoas_Link = encoder.encodeObject();
    String href = object.getHref();
    if (href != null && href.length() != 0) {
      encoder_io_micronaut_http_hateoas_Link.encodeKey("href");
      String tmp = href;
      if (tmp == null) {
        encoder_io_micronaut_http_hateoas_Link.encodeNull();
      } else {
        encoder_io_micronaut_http_hateoas_Link.encodeString(tmp);
      }
    }
    encoder_io_micronaut_http_hateoas_Link.encodeKey("templated");
    encoder_io_micronaut_http_hateoas_Link.encodeBoolean(object.isTemplated());
    Optional<MediaType> type = object.getType();
    if (type.isPresent() && !this.io_micronaut_json_Serializer___super_io_micronaut_http_MediaType_.isEmpty(type.get())) {
      encoder_io_micronaut_http_hateoas_Link.encodeKey("type");
      Optional<MediaType> tmp_ = type;
      if (tmp_.isPresent()) {
        this.io_micronaut_json_Serializer___super_io_micronaut_http_MediaType_.serialize(encoder_io_micronaut_http_hateoas_Link, tmp_.get());
      } else {
        encoder_io_micronaut_http_hateoas_Link.encodeNull();
      }
    }
    Optional<String> deprecation = object.getDeprecation();
    if (deprecation.isPresent() && deprecation.get().length() != 0) {
      encoder_io_micronaut_http_hateoas_Link.encodeKey("deprecation");
      Optional<String> tmp__ = deprecation;
      if (tmp__.isPresent()) {
        encoder_io_micronaut_http_hateoas_Link.encodeString(tmp__.get());
      } else {
        encoder_io_micronaut_http_hateoas_Link.encodeNull();
      }
    }
    Optional<String> profile = object.getProfile();
    if (profile.isPresent() && profile.get().length() != 0) {
      encoder_io_micronaut_http_hateoas_Link.encodeKey("profile");
      Optional<String> tmp___ = profile;
      if (tmp___.isPresent()) {
        encoder_io_micronaut_http_hateoas_Link.encodeString(tmp___.get());
      } else {
        encoder_io_micronaut_http_hateoas_Link.encodeNull();
      }
    }
    Optional<String> name = object.getName();
    if (name.isPresent() && name.get().length() != 0) {
      encoder_io_micronaut_http_hateoas_Link.encodeKey("name");
      Optional<String> tmp____ = name;
      if (tmp____.isPresent()) {
        encoder_io_micronaut_http_hateoas_Link.encodeString(tmp____.get());
      } else {
        encoder_io_micronaut_http_hateoas_Link.encodeNull();
      }
    }
    Optional<String> title = object.getTitle();
    if (title.isPresent() && title.get().length() != 0) {
      encoder_io_micronaut_http_hateoas_Link.encodeKey("title");
      Optional<String> tmp_____ = title;
      if (tmp_____.isPresent()) {
        encoder_io_micronaut_http_hateoas_Link.encodeString(tmp_____.get());
      } else {
        encoder_io_micronaut_http_hateoas_Link.encodeNull();
      }
    }
    Optional<String> hreflang = object.getHreflang();
    if (hreflang.isPresent() && hreflang.get().length() != 0) {
      encoder_io_micronaut_http_hateoas_Link.encodeKey("hreflang");
      Optional<String> tmp______ = hreflang;
      if (tmp______.isPresent()) {
        encoder_io_micronaut_http_hateoas_Link.encodeString(tmp______.get());
      } else {
        encoder_io_micronaut_http_hateoas_Link.encodeNull();
      }
    }
    encoder_io_micronaut_http_hateoas_Link.finishStructure();
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = Link.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(Link.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $io_micronaut_http_hateoas_Link$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $io_micronaut_http_hateoas_Link$Serializer(locator.findContravariantSerializer(Argument.of(MediaType.class)));
    }
  }
}
