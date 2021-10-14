package io.micronaut.json.generated.serializer;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.json.Encoder;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import io.micronaut.logging.LogLevel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.lang.IncompatibleClassChangeError;
import java.lang.Override;
import java.lang.String;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $io_micronaut_logging_LogLevel$Serializer implements Serializer<LogLevel> {
  public $io_micronaut_logging_LogLevel$Serializer() {
  }

  @Override
  public void serialize(Encoder encoder, LogLevel value) throws IOException {
    switch (value) {
      case ALL: {
        encoder.encodeString("ALL");
        break;
      }
      case TRACE: {
        encoder.encodeString("TRACE");
        break;
      }
      case DEBUG: {
        encoder.encodeString("DEBUG");
        break;
      }
      case INFO: {
        encoder.encodeString("INFO");
        break;
      }
      case WARN: {
        encoder.encodeString("WARN");
        break;
      }
      case ERROR: {
        encoder.encodeString("ERROR");
        break;
      }
      case OFF: {
        encoder.encodeString("OFF");
        break;
      }
      case NOT_SPECIFIED: {
        encoder.encodeString("NOT_SPECIFIED");
        break;
      }
      default: {
        throw new IncompatibleClassChangeError();
      }
    }
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = LogLevel.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(LogLevel.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $io_micronaut_logging_LogLevel$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $io_micronaut_logging_LogLevel$Serializer();
    }
  }
}
