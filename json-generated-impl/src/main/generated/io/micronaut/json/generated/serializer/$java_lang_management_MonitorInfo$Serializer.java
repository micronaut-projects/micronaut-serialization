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
import java.lang.management.MonitorInfo;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $java_lang_management_MonitorInfo$Serializer implements Serializer<MonitorInfo> {
  private final Serializer<? super StackTraceElement> io_micronaut_json_Serializer___super_java_lang_StackTraceElement_;

  public $java_lang_management_MonitorInfo$Serializer(
      Serializer<? super StackTraceElement> io_micronaut_json_Serializer___super_java_lang_StackTraceElement_) {
    this.io_micronaut_json_Serializer___super_java_lang_StackTraceElement_ = io_micronaut_json_Serializer___super_java_lang_StackTraceElement_;
  }

  @Override
  public void serialize(Encoder encoder, MonitorInfo value) throws IOException {
    MonitorInfo object = value;
    Encoder encoder_java_lang_management_MonitorInfo = encoder.encodeObject();
    encoder_java_lang_management_MonitorInfo.encodeKey("lockedStackDepth");
    encoder_java_lang_management_MonitorInfo.encodeInt(object.getLockedStackDepth());
    StackTraceElement lockedStackFrame = object.getLockedStackFrame();
    if (lockedStackFrame != null && !this.io_micronaut_json_Serializer___super_java_lang_StackTraceElement_.isEmpty(lockedStackFrame)) {
      encoder_java_lang_management_MonitorInfo.encodeKey("lockedStackFrame");
      StackTraceElement tmp = lockedStackFrame;
      if (tmp == null) {
        encoder_java_lang_management_MonitorInfo.encodeNull();
      } else {
        this.io_micronaut_json_Serializer___super_java_lang_StackTraceElement_.serialize(encoder_java_lang_management_MonitorInfo, tmp);
      }
    }
    encoder_java_lang_management_MonitorInfo.finishStructure();
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = MonitorInfo.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(MonitorInfo.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_lang_management_MonitorInfo$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_lang_management_MonitorInfo$Serializer(locator.findContravariantSerializer(Argument.of(StackTraceElement.class)));
    }
  }
}
