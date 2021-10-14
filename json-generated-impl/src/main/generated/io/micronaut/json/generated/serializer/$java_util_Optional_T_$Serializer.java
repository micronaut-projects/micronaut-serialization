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
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Function;

final class $java_util_Optional_T_$Serializer<T> implements Serializer<Optional<T>> {
  private final Serializer<? super T> io_micronaut_json_Serializer___super_T_;

  public $java_util_Optional_T_$Serializer(
      Serializer<? super T> io_micronaut_json_Serializer___super_T_) {
    this.io_micronaut_json_Serializer___super_T_ = io_micronaut_json_Serializer___super_T_;
  }

  @Override
  public void serialize(Encoder encoder, Optional<T> value) throws IOException {
    Optional<T> tmp = value;
    if (tmp.isPresent()) {
      this.io_micronaut_json_Serializer___super_T_.serialize(encoder, tmp.get());
    } else {
      encoder.encodeNull();
    }
  }

  @Override
  public boolean isEmpty(Optional<T> value) {
    return !(value.isPresent() && !this.io_micronaut_json_Serializer___super_T_.isEmpty(value.get()));
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = Optional.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(Optional.class, Argument.of(Object.class, "T"));

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_util_Optional_T_$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_util_Optional_T_$Serializer(locator.findContravariantSerializer(getTypeParameter.apply("T")));
    }
  }
}
