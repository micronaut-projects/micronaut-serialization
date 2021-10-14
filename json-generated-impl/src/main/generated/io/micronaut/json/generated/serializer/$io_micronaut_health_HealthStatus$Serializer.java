package io.micronaut.json.generated.serializer;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.health.HealthStatus;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $io_micronaut_health_HealthStatus$Serializer implements Serializer<HealthStatus> {
  public $io_micronaut_health_HealthStatus$Serializer() {
  }

  @Override
  public void serialize(Encoder encoder, HealthStatus value) throws IOException {
    String tmp = value.toString();
    if (tmp == null) {
      encoder.encodeNull();
    } else {
      encoder.encodeString(tmp);
    }
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = HealthStatus.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(HealthStatus.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $io_micronaut_health_HealthStatus$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $io_micronaut_health_HealthStatus$Serializer();
    }
  }
}
