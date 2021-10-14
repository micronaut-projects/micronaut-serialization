package io.micronaut.json.generated.serializer;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.cookie.SameSite;
import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $io_micronaut_http_cookie_SameSite$Deserializer implements Deserializer<SameSite> {
  public $io_micronaut_http_cookie_SameSite$Deserializer() {
  }

  @Override
  public SameSite deserialize(Decoder decoder) throws IOException {
    switch (decoder.decodeString()) {
      case "Lax": {
        return SameSite.Lax;
      }
      case "Strict": {
        return SameSite.Strict;
      }
      case "None": {
        return SameSite.None;
      }
      default: {
        throw decoder.createDeserializationException("Bad enum value for field io.micronaut.http.cookie.SameSite");
      }
    }
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = SameSite.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Deserializer.Factory {
    private static final Argument TYPE = Argument.of(SameSite.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $io_micronaut_http_cookie_SameSite$Deserializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $io_micronaut_http_cookie_SameSite$Deserializer();
    }
  }
}
