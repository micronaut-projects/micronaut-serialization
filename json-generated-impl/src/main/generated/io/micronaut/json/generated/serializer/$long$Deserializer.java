package io.micronaut.json.generated.serializer;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.lang.Long;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $long$Deserializer implements Deserializer<Long> {
  public $long$Deserializer() {
  }

  @Override
  public Long deserialize(Decoder decoder) throws IOException {
    return decoder.decodeLong();
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = long.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Deserializer.Factory {
    private static final Argument TYPE = Argument.of(long.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $long$Deserializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $long$Deserializer();
    }
  }
}
