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
import java.lang.IncompatibleClassChangeError;
import java.lang.Override;
import java.lang.StackTraceElement;
import java.lang.String;
import java.lang.Thread;
import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Type;
import java.util.function.Function;

final class $java_lang_management_ThreadInfo$Serializer implements Serializer<ThreadInfo> {
  private final Serializer<? super LockInfo> io_micronaut_json_Serializer___super_java_lang_management_LockInfo_;

  private final Serializer<? super StackTraceElement> io_micronaut_json_Serializer___super_java_lang_StackTraceElement_;

  private final Serializer<? super MonitorInfo> io_micronaut_json_Serializer___super_java_lang_management_MonitorInfo_;

  public $java_lang_management_ThreadInfo$Serializer(
      Serializer<? super LockInfo> io_micronaut_json_Serializer___super_java_lang_management_LockInfo_,
      Serializer<? super StackTraceElement> io_micronaut_json_Serializer___super_java_lang_StackTraceElement_,
      Serializer<? super MonitorInfo> io_micronaut_json_Serializer___super_java_lang_management_MonitorInfo_) {
    this.io_micronaut_json_Serializer___super_java_lang_management_LockInfo_ = io_micronaut_json_Serializer___super_java_lang_management_LockInfo_;
    this.io_micronaut_json_Serializer___super_java_lang_StackTraceElement_ = io_micronaut_json_Serializer___super_java_lang_StackTraceElement_;
    this.io_micronaut_json_Serializer___super_java_lang_management_MonitorInfo_ = io_micronaut_json_Serializer___super_java_lang_management_MonitorInfo_;
  }

  @Override
  public void serialize(Encoder encoder, ThreadInfo value) throws IOException {
    ThreadInfo object = value;
    Encoder encoder_java_lang_management_ThreadInfo = encoder.encodeObject();
    encoder_java_lang_management_ThreadInfo.encodeKey("threadId");
    encoder_java_lang_management_ThreadInfo.encodeLong(object.getThreadId());
    String threadName = object.getThreadName();
    if (threadName != null && threadName.length() != 0) {
      encoder_java_lang_management_ThreadInfo.encodeKey("threadName");
      String tmp = threadName;
      if (tmp == null) {
        encoder_java_lang_management_ThreadInfo.encodeNull();
      } else {
        encoder_java_lang_management_ThreadInfo.encodeString(tmp);
      }
    }
    Thread.State threadState = object.getThreadState();
    if (threadState != null) {
      encoder_java_lang_management_ThreadInfo.encodeKey("threadState");
      Thread.State tmp_ = threadState;
      if (tmp_ == null) {
        encoder_java_lang_management_ThreadInfo.encodeNull();
      } else {
        switch (tmp_) {
          case NEW: {
            encoder_java_lang_management_ThreadInfo.encodeString("NEW");
            break;
          }
          case RUNNABLE: {
            encoder_java_lang_management_ThreadInfo.encodeString("RUNNABLE");
            break;
          }
          case BLOCKED: {
            encoder_java_lang_management_ThreadInfo.encodeString("BLOCKED");
            break;
          }
          case WAITING: {
            encoder_java_lang_management_ThreadInfo.encodeString("WAITING");
            break;
          }
          case TIMED_WAITING: {
            encoder_java_lang_management_ThreadInfo.encodeString("TIMED_WAITING");
            break;
          }
          case TERMINATED: {
            encoder_java_lang_management_ThreadInfo.encodeString("TERMINATED");
            break;
          }
          default: {
            throw new IncompatibleClassChangeError();
          }
        }
      }
    }
    encoder_java_lang_management_ThreadInfo.encodeKey("blockedTime");
    encoder_java_lang_management_ThreadInfo.encodeLong(object.getBlockedTime());
    encoder_java_lang_management_ThreadInfo.encodeKey("blockedCount");
    encoder_java_lang_management_ThreadInfo.encodeLong(object.getBlockedCount());
    encoder_java_lang_management_ThreadInfo.encodeKey("waitedTime");
    encoder_java_lang_management_ThreadInfo.encodeLong(object.getWaitedTime());
    encoder_java_lang_management_ThreadInfo.encodeKey("waitedCount");
    encoder_java_lang_management_ThreadInfo.encodeLong(object.getWaitedCount());
    LockInfo lockInfo = object.getLockInfo();
    if (lockInfo != null && !this.io_micronaut_json_Serializer___super_java_lang_management_LockInfo_.isEmpty(lockInfo)) {
      encoder_java_lang_management_ThreadInfo.encodeKey("lockInfo");
      LockInfo tmp__ = lockInfo;
      if (tmp__ == null) {
        encoder_java_lang_management_ThreadInfo.encodeNull();
      } else {
        this.io_micronaut_json_Serializer___super_java_lang_management_LockInfo_.serialize(encoder_java_lang_management_ThreadInfo, tmp__);
      }
    }
    String lockName = object.getLockName();
    if (lockName != null && lockName.length() != 0) {
      encoder_java_lang_management_ThreadInfo.encodeKey("lockName");
      String tmp___ = lockName;
      if (tmp___ == null) {
        encoder_java_lang_management_ThreadInfo.encodeNull();
      } else {
        encoder_java_lang_management_ThreadInfo.encodeString(tmp___);
      }
    }
    encoder_java_lang_management_ThreadInfo.encodeKey("lockOwnerId");
    encoder_java_lang_management_ThreadInfo.encodeLong(object.getLockOwnerId());
    String lockOwnerName = object.getLockOwnerName();
    if (lockOwnerName != null && lockOwnerName.length() != 0) {
      encoder_java_lang_management_ThreadInfo.encodeKey("lockOwnerName");
      String tmp____ = lockOwnerName;
      if (tmp____ == null) {
        encoder_java_lang_management_ThreadInfo.encodeNull();
      } else {
        encoder_java_lang_management_ThreadInfo.encodeString(tmp____);
      }
    }
    StackTraceElement[] stackTrace = object.getStackTrace();
    if (stackTrace != null && stackTrace.length != 0) {
      encoder_java_lang_management_ThreadInfo.encodeKey("stackTrace");
      StackTraceElement[] tmp_____ = stackTrace;
      if (tmp_____ == null) {
        encoder_java_lang_management_ThreadInfo.encodeNull();
      } else {
        Encoder arrayEncoder = encoder_java_lang_management_ThreadInfo.encodeArray();
        for (StackTraceElement item : tmp_____) {
          StackTraceElement tmp______ = item;
          if (tmp______ == null) {
            arrayEncoder.encodeNull();
          } else {
            this.io_micronaut_json_Serializer___super_java_lang_StackTraceElement_.serialize(arrayEncoder, tmp______);
          }
        }
        arrayEncoder.finishStructure();
      }
    }
    encoder_java_lang_management_ThreadInfo.encodeKey("suspended");
    encoder_java_lang_management_ThreadInfo.encodeBoolean(object.isSuspended());
    encoder_java_lang_management_ThreadInfo.encodeKey("inNative");
    encoder_java_lang_management_ThreadInfo.encodeBoolean(object.isInNative());
    MonitorInfo[] lockedMonitors = object.getLockedMonitors();
    if (lockedMonitors != null && lockedMonitors.length != 0) {
      encoder_java_lang_management_ThreadInfo.encodeKey("lockedMonitors");
      MonitorInfo[] tmp_______ = lockedMonitors;
      if (tmp_______ == null) {
        encoder_java_lang_management_ThreadInfo.encodeNull();
      } else {
        Encoder arrayEncoder_ = encoder_java_lang_management_ThreadInfo.encodeArray();
        for (MonitorInfo item_ : tmp_______) {
          MonitorInfo tmp________ = item_;
          if (tmp________ == null) {
            arrayEncoder_.encodeNull();
          } else {
            this.io_micronaut_json_Serializer___super_java_lang_management_MonitorInfo_.serialize(arrayEncoder_, tmp________);
          }
        }
        arrayEncoder_.finishStructure();
      }
    }
    LockInfo[] lockedSynchronizers = object.getLockedSynchronizers();
    if (lockedSynchronizers != null && lockedSynchronizers.length != 0) {
      encoder_java_lang_management_ThreadInfo.encodeKey("lockedSynchronizers");
      LockInfo[] tmp_________ = lockedSynchronizers;
      if (tmp_________ == null) {
        encoder_java_lang_management_ThreadInfo.encodeNull();
      } else {
        Encoder arrayEncoder__ = encoder_java_lang_management_ThreadInfo.encodeArray();
        for (LockInfo item__ : tmp_________) {
          LockInfo tmp__________ = item__;
          if (tmp__________ == null) {
            arrayEncoder__.encodeNull();
          } else {
            this.io_micronaut_json_Serializer___super_java_lang_management_LockInfo_.serialize(arrayEncoder__, tmp__________);
          }
        }
        arrayEncoder__.finishStructure();
      }
    }
    encoder_java_lang_management_ThreadInfo.finishStructure();
  }

  @Singleton
  @BootstrapContextCompatible
  @Requires(
      classes = ThreadInfo.class,
      beans = SerializerLocator.class
  )
  public static final class FactoryImpl implements Serializer.Factory {
    private static final Argument TYPE = Argument.of(ThreadInfo.class);

    @Inject
    public FactoryImpl() {
    }

    @Override
    public Argument getGenericType() {
      return TYPE;
    }

    @Override
    public $java_lang_management_ThreadInfo$Serializer newInstance(SerializerLocator locator,
        Function<String, Type> getTypeParameter) {
      return new $java_lang_management_ThreadInfo$Serializer(locator.findContravariantSerializer(Argument.of(LockInfo.class)), locator.findContravariantSerializer(Argument.of(StackTraceElement.class)), locator.findContravariantSerializer(Argument.of(MonitorInfo.class)));
    }
  }
}
