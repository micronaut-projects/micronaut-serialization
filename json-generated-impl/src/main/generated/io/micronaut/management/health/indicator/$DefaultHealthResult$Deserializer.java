package io.micronaut.management.health.indicator;

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

final class $DefaultHealthResult$Deserializer implements Deserializer<DefaultHealthResult> {
  private final Deserializer<?> io_micronaut_json_Deserializer___;

  public $DefaultHealthResult$Deserializer(Deserializer<?> io_micronaut_json_Deserializer___) {
    this.io_micronaut_json_Deserializer___ = io_micronaut_json_Deserializer___;
  }

  @Override
  public DefaultHealthResult deserialize(Decoder decoder) throws IOException {
    String name = null;
    String status = null;
    Map<String, Object> details = null;
    long readProperties = 0;
    Decoder elementDecoder = decoder.decodeObject();
    while (true) {
      String fieldName = elementDecoder.decodeKey();
      if (fieldName == null) break;
      switch (fieldName) {
        case "name":
          if ((readProperties & 0x2L) != 0) throw elementDecoder.createDeserializationException("Duplicate property name");
          readProperties |= 0x2L;
          if (elementDecoder.decodeNull()) {
            name = null;
          } else {
            name = elementDecoder.decodeString();
          }
          break;
        case "status":
          if ((readProperties & 0x4L) != 0) throw elementDecoder.createDeserializationException("Duplicate property status");
          readProperties |= 0x4L;
          if (elementDecoder.decodeNull()) {
            status = null;
          } else {
            status = elementDecoder.decodeString();
          }
          break;
        case "details":
          if ((readProperties & 0x1L) != 0) throw elementDecoder.createDeserializationException("Duplicate property details");
          readProperties |= 0x1L;
          if (elementDecoder.decodeNull()) {
            details = null;
          } else {
            Decoder mapDecoder = elementDecoder.decodeObject();
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            String key;
            while ((key = mapDecoder.decodeKey()) != null) {
              if (mapDecoder.decodeNull()) {
                map.put(key, null);
              } else {
                map.put(key, this.io_micronaut_json_Deserializer___.deserialize(mapDecoder));
              }
            }
            mapDecoder.finishStructure();
            details = map;
          }
          break;
        default: {
          elementDecoder.skipValue();
        }
      }
    }
    elementDecoder.finishStructure();
    DefaultHealthResult result = new DefaultHealthResult(name, status, details);
    if ((readProperties & 0x2L) != 0) {
    }
    if ((readProperties & 0x4L) != 0) {
    }
    if ((readProperties & 0x1L) != 0) {
    }
    return result;
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = DefaultHealthResult.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Deserializer.Factory {
    private static final Argument TYPE = Argument.of(DefaultHealthResult.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $DefaultHealthResult$Deserializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $DefaultHealthResult$Deserializer(locator.findInvariantDeserializer(Argument.of(Object.class)));
    }
  }
}
