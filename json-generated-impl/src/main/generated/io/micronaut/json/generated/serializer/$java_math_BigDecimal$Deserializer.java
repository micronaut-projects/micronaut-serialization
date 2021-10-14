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
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.function.Function;

final class $java_math_BigDecimal$Deserializer implements Deserializer<BigDecimal> {
  public $java_math_BigDecimal$Deserializer() {
  }

  @Override
  public BigDecimal deserialize(Decoder decoder) throws IOException {
    return decoder.decodeBigDecimal();
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = BigDecimal.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Deserializer.Factory {
    private static final Argument TYPE = Argument.of(BigDecimal.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_math_BigDecimal$Deserializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_math_BigDecimal$Deserializer();
    }
  }
}
