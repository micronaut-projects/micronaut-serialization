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
import java.util.List;
import java.util.function.Function;

final class $java_util_List_E_$Serializer<E> implements Serializer<List<E>> {
  private final Serializer<? super E> io_micronaut_json_Serializer___super_E_;

  public $java_util_List_E_$Serializer(
      Serializer<? super E> io_micronaut_json_Serializer___super_E_) {
    this.io_micronaut_json_Serializer___super_E_ = io_micronaut_json_Serializer___super_E_;
  }

  @Override
  public void serialize(Encoder encoder, List<E> value) throws IOException {
    Encoder arrayEncoder = encoder.encodeArray();
    for (E item : value) {
      E tmp = item;
      if (tmp == null) {
        arrayEncoder.encodeNull();
      } else {
        this.io_micronaut_json_Serializer___super_E_.serialize(arrayEncoder, tmp);
      }
    }
    arrayEncoder.finishStructure();
  }

  @Override
  public boolean isEmpty(List<E> value) {
    return !(!value.isEmpty());
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = List.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(List.class, Argument.of(Object.class, "E"));

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_util_List_E_$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_util_List_E_$Serializer(locator.findContravariantSerializer(getTypeParameter.apply("E")));
    }
  }
}
