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
import java.lang.management.LockInfo;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $java_lang_management_LockInfo$Serializer implements Serializer<LockInfo> {
  public $java_lang_management_LockInfo$Serializer() {
  }

  @Override
  public void serialize(Encoder encoder, LockInfo value) throws IOException {
    LockInfo object = value;
    Encoder encoder_java_lang_management_LockInfo = encoder.encodeObject();
    String className = object.getClassName();
    if (className != null && className.length() != 0) {
      encoder_java_lang_management_LockInfo.encodeKey("className");
      String tmp = className;
      if (tmp == null) {
        encoder_java_lang_management_LockInfo.encodeNull();
      } else {
        encoder_java_lang_management_LockInfo.encodeString(tmp);
      }
    }
    encoder_java_lang_management_LockInfo.encodeKey("identityHashCode");
    encoder_java_lang_management_LockInfo.encodeInt(object.getIdentityHashCode());
    encoder_java_lang_management_LockInfo.finishStructure();
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = LockInfo.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(LockInfo.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_lang_management_LockInfo$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_lang_management_LockInfo$Serializer();
    }
  }
}
