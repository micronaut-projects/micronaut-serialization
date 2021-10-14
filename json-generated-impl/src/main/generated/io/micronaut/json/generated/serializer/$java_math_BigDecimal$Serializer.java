package io.micronaut.json.generated.serializer;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
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
import java.util.function.Function;

final class $java_math_BigDecimal$Serializer implements Serializer<BigDecimal> {
  public $java_math_BigDecimal$Serializer() {
  }

  @Override
  public void serialize(Encoder encoder, BigDecimal value) throws IOException {
    encoder.encodeBigDecimal(value);
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = BigDecimal.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(BigDecimal.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_math_BigDecimal$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_math_BigDecimal$Serializer();
    }
  }
}
