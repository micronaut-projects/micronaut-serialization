package io.micronaut.json.generated.serializer;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.SerializerLocator;
import io.micronaut.logging.LogLevel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $io_micronaut_logging_LogLevel$Deserializer implements Deserializer<LogLevel> {
  public $io_micronaut_logging_LogLevel$Deserializer() {
  }

  @Override
  public LogLevel deserialize(Decoder decoder) throws IOException {
    switch (decoder.decodeString()) {
      case "ALL": {
        return LogLevel.ALL;
      }
      case "TRACE": {
        return LogLevel.TRACE;
      }
      case "DEBUG": {
        return LogLevel.DEBUG;
      }
      case "INFO": {
        return LogLevel.INFO;
      }
      case "WARN": {
        return LogLevel.WARN;
      }
      case "ERROR": {
        return LogLevel.ERROR;
      }
      case "OFF": {
        return LogLevel.OFF;
      }
      case "NOT_SPECIFIED": {
        return LogLevel.NOT_SPECIFIED;
      }
      default: {
        throw decoder.createDeserializationException("Bad enum value for field io.micronaut.logging.LogLevel");
      }
    }
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = LogLevel.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Deserializer.Factory {
    private static final Argument TYPE = Argument.of(LogLevel.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $io_micronaut_logging_LogLevel$Deserializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $io_micronaut_logging_LogLevel$Deserializer();
    }
  }
}
