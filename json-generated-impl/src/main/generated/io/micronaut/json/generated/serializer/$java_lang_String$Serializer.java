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
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $java_lang_String$Serializer implements Serializer<String> {
  public $java_lang_String$Serializer() {
  }

  @Override
  public void serialize(Encoder encoder, String value) throws IOException {
    encoder.encodeString(value);
  }

  @Override
  public boolean isEmpty(String value) {
    return !(value.length() != 0);
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = String.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(String.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_lang_String$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_lang_String$Serializer();
    }
  }
}
