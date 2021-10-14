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
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Function;

final class $java_util_Optional_T_$Deserializer<T> implements Deserializer<Optional<T>> {
  private final Deserializer<? extends T> io_micronaut_json_Deserializer___extends_T_;

  public $java_util_Optional_T_$Deserializer(
      Deserializer<? extends T> io_micronaut_json_Deserializer___extends_T_) {
    this.io_micronaut_json_Deserializer___extends_T_ = io_micronaut_json_Deserializer___extends_T_;
  }

  @Override
  public Optional<T> deserialize(Decoder decoder) throws IOException {
    if (decoder.decodeNull()) {
      return Optional.empty();
    } else {
      return Optional.of(this.io_micronaut_json_Deserializer___extends_T_.deserialize(decoder));
    }
  }

  @Override
  public boolean allowNull() {
    return true;
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = Optional.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Deserializer.Factory {
    private static final Argument TYPE = Argument.of(Optional.class, Argument.of(Object.class, "T"));

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_util_Optional_T_$Deserializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_util_Optional_T_$Deserializer(locator.findInvariantDeserializer(getTypeParameter.apply("T")));
    }
  }
}
