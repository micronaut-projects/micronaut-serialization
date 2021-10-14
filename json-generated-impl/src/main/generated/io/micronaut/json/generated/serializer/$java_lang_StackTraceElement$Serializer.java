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
import java.lang.StackTraceElement;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $java_lang_StackTraceElement$Serializer implements Serializer<StackTraceElement> {
  public $java_lang_StackTraceElement$Serializer() {
  }

  @Override
  public void serialize(Encoder encoder, StackTraceElement value) throws IOException {
    StackTraceElement object = value;
    Encoder encoder_java_lang_StackTraceElement = encoder.encodeObject();
    String fileName = object.getFileName();
    if (fileName != null && fileName.length() != 0) {
      encoder_java_lang_StackTraceElement.encodeKey("fileName");
      String tmp = fileName;
      if (tmp == null) {
        encoder_java_lang_StackTraceElement.encodeNull();
      } else {
        encoder_java_lang_StackTraceElement.encodeString(tmp);
      }
    }
    encoder_java_lang_StackTraceElement.encodeKey("lineNumber");
    encoder_java_lang_StackTraceElement.encodeInt(object.getLineNumber());
    String className = object.getClassName();
    if (className != null && className.length() != 0) {
      encoder_java_lang_StackTraceElement.encodeKey("className");
      String tmp_ = className;
      if (tmp_ == null) {
        encoder_java_lang_StackTraceElement.encodeNull();
      } else {
        encoder_java_lang_StackTraceElement.encodeString(tmp_);
      }
    }
    String methodName = object.getMethodName();
    if (methodName != null && methodName.length() != 0) {
      encoder_java_lang_StackTraceElement.encodeKey("methodName");
      String tmp__ = methodName;
      if (tmp__ == null) {
        encoder_java_lang_StackTraceElement.encodeNull();
      } else {
        encoder_java_lang_StackTraceElement.encodeString(tmp__);
      }
    }
    encoder_java_lang_StackTraceElement.encodeKey("nativeMethod");
    encoder_java_lang_StackTraceElement.encodeBoolean(object.isNativeMethod());
    encoder_java_lang_StackTraceElement.finishStructure();
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = StackTraceElement.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(StackTraceElement.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_lang_StackTraceElement$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_lang_StackTraceElement$Serializer();
    }
  }
}
