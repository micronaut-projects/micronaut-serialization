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
import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $int$Deserializer implements Deserializer<Integer> {
  public $int$Deserializer() {
  }

  @Override
  public Integer deserialize(Decoder decoder) throws IOException {
    return decoder.decodeInt();
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = int.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Deserializer.Factory {
    private static final Argument TYPE = Argument.of(int.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $int$Deserializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $int$Deserializer();
    }
  }
}
