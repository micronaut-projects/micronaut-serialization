package io.micronaut.json.generated.serializer;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.cookie.SameSite;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.lang.IncompatibleClassChangeError;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $io_micronaut_http_cookie_SameSite$Serializer implements Serializer<SameSite> {
  public $io_micronaut_http_cookie_SameSite$Serializer() {
  }

  @Override
  public void serialize(Encoder encoder, SameSite value) throws IOException {
    switch (value) {
      case Lax: {
        encoder.encodeString("Lax");
        break;
      }
      case Strict: {
        encoder.encodeString("Strict");
        break;
      }
      case None: {
        encoder.encodeString("None");
        break;
      }
      default: {
        throw new IncompatibleClassChangeError();
      }
    }
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = SameSite.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(SameSite.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $io_micronaut_http_cookie_SameSite$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $io_micronaut_http_cookie_SameSite$Serializer();
    }
  }
}
