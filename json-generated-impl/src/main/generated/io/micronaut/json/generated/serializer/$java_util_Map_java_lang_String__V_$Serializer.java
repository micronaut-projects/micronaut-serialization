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
import java.util.Map;
import java.util.function.Function;

final class $java_util_Map_java_lang_String__V_$Serializer<V> implements Serializer<Map<String, V>> {
  private final Serializer<? super V> io_micronaut_json_Serializer___super_V_;

  public $java_util_Map_java_lang_String__V_$Serializer(
      Serializer<? super V> io_micronaut_json_Serializer___super_V_) {
    this.io_micronaut_json_Serializer___super_V_ = io_micronaut_json_Serializer___super_V_;
  }

  @Override
  public void serialize(Encoder encoder, Map<String, V> value) throws IOException {
    Encoder mapEncoder = encoder.encodeObject();
    for (Map.Entry<String, V> entry : value.entrySet()) {
      mapEncoder.encodeKey(entry.getKey());
      V tmp = entry.getValue();
      if (tmp == null) {
        mapEncoder.encodeNull();
      } else {
        this.io_micronaut_json_Serializer___super_V_.serialize(mapEncoder, tmp);
      }
    }
    mapEncoder.finishStructure();
  }

  @Override
  public boolean isEmpty(Map<String, V> value) {
    return !(!value.isEmpty());
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = Map.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(Map.class, Argument.of(String.class), Argument.of(Object.class, "V"));

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_util_Map_java_lang_String__V_$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_util_Map_java_lang_String__V_$Serializer(locator.findContravariantSerializer(getTypeParameter.apply("V")));
    }
  }
}
