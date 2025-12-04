package io.micronaut.serde.jackson.generic.scenario3;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.annotation.Generated;

/**
 * Status is a return value for calls that don&#39;t return other objects.
 */
@Serdeable
@JsonPropertyOrder({
    V1Status.JSON_PROPERTY_API_VERSION,
    V1Status.JSON_PROPERTY_CODE,
    V1Status.JSON_PROPERTY_DETAILS,
    V1Status.JSON_PROPERTY_KIND,
    V1Status.JSON_PROPERTY_MESSAGE,
    V1Status.JSON_PROPERTY_METADATA,
    V1Status.JSON_PROPERTY_REASON,
    V1Status.JSON_PROPERTY_STATUS
})
@Generated("io.micronaut.openapi.generator.JavaMicronautClientCodegen")
public class V1Status {

    public static final String JSON_PROPERTY_API_VERSION = "apiVersion";
    public static final String JSON_PROPERTY_CODE = "code";
    public static final String JSON_PROPERTY_DETAILS = "details";
    public static final String JSON_PROPERTY_KIND = "kind";
    public static final String JSON_PROPERTY_MESSAGE = "message";
    public static final String JSON_PROPERTY_METADATA = "metadata";
    public static final String JSON_PROPERTY_REASON = "reason";
    public static final String JSON_PROPERTY_STATUS = "status";

    /**
     * APIVersion defines the versioned schema of this representation of an object. Servers should convert recognized schemas to the latest internal value, and may reject unrecognized values. More info: https://git.k8s.io/community/contributors/devel/sig-architecture/api-conventions.md#resources
     */
    @Nullable
    @JsonProperty(JSON_PROPERTY_API_VERSION)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private String apiVersion;

    /**
     * Suggested HTTP return code for this status, 0 if not set.
     */
    @Nullable
    @JsonProperty(JSON_PROPERTY_CODE)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private Integer code;

    /**
     * Kind is a string value representing the REST resource this object represents. Servers may infer this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More info: https://git.k8s.io/community/contributors/devel/sig-architecture/api-conventions.md#types-kinds
     */
    @Nullable
    @JsonProperty(JSON_PROPERTY_KIND)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private String kind;

    /**
     * A human-readable description of the status of this operation.
     */
    @Nullable
    @JsonProperty(JSON_PROPERTY_MESSAGE)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private String message;

    /**
     * A machine-readable description of why this operation is in the \&quot;Failure\&quot; status. If this value is empty there is no information available. A Reason clarifies an HTTP status code but does not override it.
     */
    @Nullable
    @JsonProperty(JSON_PROPERTY_REASON)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private String reason;

    /**
     * Status of the operation. One of: \&quot;Success\&quot; or \&quot;Failure\&quot;. More info: https://git.k8s.io/community/contributors/devel/sig-architecture/api-conventions.md#spec-and-status
     */
    @Nullable
    @JsonProperty(JSON_PROPERTY_STATUS)
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private String status;

    /**
     * APIVersion defines the versioned schema of this representation of an object. Servers should convert recognized schemas to the latest internal value, and may reject unrecognized values. More info: https://git.k8s.io/community/contributors/devel/sig-architecture/api-conventions.md#resources
     *
     * @return the apiVersion property value
     */
    public String getApiVersion() {
        return apiVersion;
    }

    /**
     * Set the apiVersion property value
     *
     * @param apiVersion property value to set
     */
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    /**
     * Set apiVersion in a chainable fashion.
     *
     * @return The same instance of V1Status for chaining.
     */
    public V1Status apiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
        return this;
    }

    /**
     * Suggested HTTP return code for this status, 0 if not set.
     *
     * @return the code property value
     */
    public Integer getCode() {
        return code;
    }

    /**
     * Set the code property value
     *
     * @param code property value to set
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * Set code in a chainable fashion.
     *
     * @return The same instance of V1Status for chaining.
     */
    public V1Status code(Integer code) {
        this.code = code;
        return this;
    }

    /**
     * Kind is a string value representing the REST resource this object represents. Servers may infer this from the endpoint the client submits requests to. Cannot be updated. In CamelCase. More info: https://git.k8s.io/community/contributors/devel/sig-architecture/api-conventions.md#types-kinds
     *
     * @return the kind property value
     */
    public String getKind() {
        return kind;
    }

    /**
     * Set the kind property value
     *
     * @param kind property value to set
     */
    public void setKind(String kind) {
        this.kind = kind;
    }

    /**
     * Set kind in a chainable fashion.
     *
     * @return The same instance of V1Status for chaining.
     */
    public V1Status kind(String kind) {
        this.kind = kind;
        return this;
    }

    /**
     * A human-readable description of the status of this operation.
     *
     * @return the message property value
     */
    public String getMessage() {
        return message;
    }

    /**
     * Set the message property value
     *
     * @param message property value to set
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Set message in a chainable fashion.
     *
     * @return The same instance of V1Status for chaining.
     */
    public V1Status message(String message) {
        this.message = message;
        return this;
    }

    /**
     * A machine-readable description of why this operation is in the \&quot;Failure\&quot; status. If this value is empty there is no information available. A Reason clarifies an HTTP status code but does not override it.
     *
     * @return the reason property value
     */
    public String getReason() {
        return reason;
    }

    /**
     * Set the reason property value
     *
     * @param reason property value to set
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * Set reason in a chainable fashion.
     *
     * @return The same instance of V1Status for chaining.
     */
    public V1Status reason(String reason) {
        this.reason = reason;
        return this;
    }

    /**
     * Status of the operation. One of: \&quot;Success\&quot; or \&quot;Failure\&quot;. More info: https://git.k8s.io/community/contributors/devel/sig-architecture/api-conventions.md#spec-and-status
     *
     * @return the status property value
     */
    public String getStatus() {
        return status;
    }

    /**
     * Set the status property value
     *
     * @param status property value to set
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Set status in a chainable fashion.
     *
     * @return The same instance of V1Status for chaining.
     */
    public V1Status status(String status) {
        this.status = status;
        return this;
    }

}
