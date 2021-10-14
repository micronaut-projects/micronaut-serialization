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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

final class $java_util_Map_java_lang_String__V_$Deserializer<V> implements Deserializer<Map<String, V>> {
  private final Deserializer<? extends V> io_micronaut_json_Deserializer___extends_V_;

  public $java_util_Map_java_lang_String__V_$Deserializer(
      Deserializer<? extends V> io_micronaut_json_Deserializer___extends_V_) {
    this.io_micronaut_json_Deserializer___extends_V_ = io_micronaut_json_Deserializer___extends_V_;
  }

  @Override
  public Map<String, V> deserialize(Decoder decoder) throws IOException {
    Decoder mapDecoder = decoder.decodeObject();
    LinkedHashMap<String, V> map = new LinkedHashMap<>();
    String key;
    while ((key = mapDecoder.decodeKey()) != null) {
      if (mapDecoder.decodeNull()) {
        map.put(key, null);
      } else {
        map.put(key, this.io_micronaut_json_Deserializer___extends_V_.deserialize(mapDecoder));
      }
    }
    mapDecoder.finishStructure();
    return map;
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = Map.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Deserializer.Factory {
    private static final Argument TYPE = Argument.of(Map.class, Argument.of(String.class), Argument.of(Object.class, "V"));

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_util_Map_java_lang_String__V_$Deserializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_util_Map_java_lang_String__V_$Deserializer(locator.findInvariantDeserializer(getTypeParameter.apply("V")));
    }
  }
}
