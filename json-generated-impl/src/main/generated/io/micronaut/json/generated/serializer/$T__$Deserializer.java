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
import java.util.ArrayList;
import java.util.function.Function;

final class $T__$Deserializer<T> implements Deserializer<T[]> {
  private final Deserializer<? extends T> io_micronaut_json_Deserializer___extends_T_;

  public $T__$Deserializer(Deserializer<? extends T> io_micronaut_json_Deserializer___extends_T_) {
    this.io_micronaut_json_Deserializer___extends_T_ = io_micronaut_json_Deserializer___extends_T_;
  }

  @Override
  public T[] deserialize(Decoder decoder) throws IOException {
    Decoder arrayDecoder = decoder.decodeArray();
    ArrayList<T> intermediate = new ArrayList<>();
    while (arrayDecoder.hasNextArrayValue()) {
      if (arrayDecoder.decodeNull()) {
        intermediate.add(null);
      } else {
        intermediate.add(this.io_micronaut_json_Deserializer___extends_T_.deserialize(arrayDecoder));
      }
    }
    arrayDecoder.finishStructure();
    return intermediate.toArray((T[]) new Object[0]);
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = Object[].class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Deserializer.Factory {
    private static final Argument TYPE = Argument.of(T[].class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $T__$Deserializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $T__$Deserializer(locator.findInvariantDeserializer(getTypeParameter.apply("T")));
    }
  }
}
