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
import java.util.List;
import java.util.function.Function;

final class $java_util_List_E_$Deserializer<E> implements Deserializer<List<E>> {
  private final Deserializer<? extends E> io_micronaut_json_Deserializer___extends_E_;

  public $java_util_List_E_$Deserializer(
      Deserializer<? extends E> io_micronaut_json_Deserializer___extends_E_) {
    this.io_micronaut_json_Deserializer___extends_E_ = io_micronaut_json_Deserializer___extends_E_;
  }

  @Override
  public List<E> deserialize(Decoder decoder) throws IOException {
    Decoder arrayDecoder = decoder.decodeArray();
    ArrayList<E> intermediate = new ArrayList<>();
    while (arrayDecoder.hasNextArrayValue()) {
      if (arrayDecoder.decodeNull()) {
        intermediate.add(null);
      } else {
        intermediate.add(this.io_micronaut_json_Deserializer___extends_E_.deserialize(arrayDecoder));
      }
    }
    arrayDecoder.finishStructure();
    return intermediate;
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = List.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Deserializer.Factory {
    private static final Argument TYPE = Argument.of(List.class, Argument.of(Object.class, "E"));

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_util_List_E_$Deserializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_util_List_E_$Deserializer(locator.findInvariantDeserializer(getTypeParameter.apply("E")));
    }
  }
}
