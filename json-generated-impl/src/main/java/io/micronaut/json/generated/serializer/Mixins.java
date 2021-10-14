package io.micronaut.json.generated.serializer;

import io.micronaut.health.HealthStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.cookie.SameSite;
import io.micronaut.http.hateoas.DefaultLink;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.json.annotation.SerializableBean;
import io.micronaut.json.annotation.SerializationMixin;
import io.micronaut.logging.LogLevel;

import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;

@SerializationMixin(forClass = DefaultLink.class, config = @SerializableBean(allowDeserialization = false))
@SerializationMixin(forClass = Link.class, config = @SerializableBean(allowDeserialization = false))
@SerializationMixin(forClass = HealthStatus.class, config = @SerializableBean(allowDeserialization = false))
@SerializationMixin(forClass = ThreadInfo.class, config = @SerializableBean(allowDeserialization = false))
@SerializationMixin(forClass = StackTraceElement.class, config = @SerializableBean(allowDeserialization = false))
@SerializationMixin(forClass = MonitorInfo.class, config = @SerializableBean(allowDeserialization = false))
@SerializationMixin(forClass = LockInfo.class, config = @SerializableBean(allowDeserialization = false))
@SerializationMixin(forClass = LogLevel.class)
@SerializationMixin(forClass = SameSite.class)
@SerializationMixin(forClass = MediaType.class, config = @SerializableBean(allowDeserialization = false))
class Mixins {
}
