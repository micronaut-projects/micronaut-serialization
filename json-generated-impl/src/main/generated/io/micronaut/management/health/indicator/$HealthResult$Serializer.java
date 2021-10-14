package io.micronaut.management.health.indicator;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.health.HealthStatus;
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
import java.util.function.Function;

final class $HealthResult$Serializer implements Serializer<HealthResult> {
  private final Serializer<? super HealthStatus> io_micronaut_json_Serializer___super_io_micronaut_health_HealthStatus_;

  private final Serializer<? super Object> io_micronaut_json_Serializer___super_java_lang_Object_;

  public $HealthResult$Serializer(
      Serializer<? super HealthStatus> io_micronaut_json_Serializer___super_io_micronaut_health_HealthStatus_,
      Serializer<? super Object> io_micronaut_json_Serializer___super_java_lang_Object_) {
    this.io_micronaut_json_Serializer___super_io_micronaut_health_HealthStatus_ = io_micronaut_json_Serializer___super_io_micronaut_health_HealthStatus_;
    this.io_micronaut_json_Serializer___super_java_lang_Object_ = io_micronaut_json_Serializer___super_java_lang_Object_;
  }

  @Override
  public void serialize(Encoder encoder, HealthResult value) throws IOException {
    HealthResult object = value;
    Encoder encoder_io_micronaut_management_health_indicator_HealthResult = encoder.encodeObject();
    String name = object.getName();
    if (name != null && name.length() != 0) {
      encoder_io_micronaut_management_health_indicator_HealthResult.encodeKey("name");
      String tmp = name;
      if (tmp == null) {
        encoder_io_micronaut_management_health_indicator_HealthResult.encodeNull();
      } else {
        encoder_io_micronaut_management_health_indicator_HealthResult.encodeString(tmp);
      }
    }
    HealthStatus status = object.getStatus();
    if (status != null && !this.io_micronaut_json_Serializer___super_io_micronaut_health_HealthStatus_.isEmpty(status)) {
      encoder_io_micronaut_management_health_indicator_HealthResult.encodeKey("status");
      HealthStatus tmp_ = status;
      if (tmp_ == null) {
        encoder_io_micronaut_management_health_indicator_HealthResult.encodeNull();
      } else {
        this.io_micronaut_json_Serializer___super_io_micronaut_health_HealthStatus_.serialize(encoder_io_micronaut_management_health_indicator_HealthResult, tmp_);
      }
    }
    Object details = object.getDetails();
    if (details != null && !this.io_micronaut_json_Serializer___super_java_lang_Object_.isEmpty(details)) {
      encoder_io_micronaut_management_health_indicator_HealthResult.encodeKey("details");
      Object tmp__ = details;
      if (tmp__ == null) {
        encoder_io_micronaut_management_health_indicator_HealthResult.encodeNull();
      } else {
        this.io_micronaut_json_Serializer___super_java_lang_Object_.serialize(encoder_io_micronaut_management_health_indicator_HealthResult, tmp__);
      }
    }
    encoder_io_micronaut_management_health_indicator_HealthResult.finishStructure();
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = HealthResult.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(HealthResult.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $HealthResult$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $HealthResult$Serializer(locator.findContravariantSerializer(Argument.of(HealthStatus.class)), locator.findContravariantSerializer(Argument.of(Object.class)));
    }
  }
}
