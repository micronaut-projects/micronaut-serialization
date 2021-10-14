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
import java.lang.Double;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $double$Serializer implements Serializer<Double> {
  public $double$Serializer() {
  }

  @Override
  public void serialize(Encoder encoder, Double value) throws IOException {
    encoder.encodeDouble(value);
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = double.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(double.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $double$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $double$Serializer();
    }
  }
}
