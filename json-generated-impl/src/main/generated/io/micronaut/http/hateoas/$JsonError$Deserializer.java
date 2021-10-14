package io.micronaut.http.hateoas;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $JsonError$Deserializer implements Deserializer<JsonError> {
  public $JsonError$Deserializer() {
  }

  @Override
  public JsonError deserialize(Decoder decoder) throws IOException {
    String message = null;
    long readProperties = 0;
    Decoder elementDecoder = decoder.decodeObject();
    while (true) {
      String fieldName = elementDecoder.decodeKey();
      if (fieldName == null) break;
      switch (fieldName) {
        case "message":
          if ((readProperties & 0x1L) != 0) throw elementDecoder.createDeserializationException("Duplicate property message");
          readProperties |= 0x1L;
          if (elementDecoder.decodeNull()) {
            message = null;
          } else {
            message = elementDecoder.decodeString();
          }
          break;
        default: {
          elementDecoder.skipValue();
        }
      }
    }
    elementDecoder.finishStructure();
    JsonError result = new JsonError();
    if ((readProperties & 0x1L) != 0) {
      result.setMessage(message);
    }
    return result;
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = JsonError.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Deserializer.Factory {
    private static final Argument TYPE = Argument.of(JsonError.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $JsonError$Deserializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $JsonError$Deserializer();
    }
  }
}
