package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * Every way a bean property can be excluded from one or both directions.
 */
@SerdeableGenerated
public class SourceGenIgnoredPropertiesBean {
    private String name;
    @JsonIgnore
    private String secret;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private long id;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    public transient String tokenSetValue;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * A getter without a setter is serialized only.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return name == null ? null : name.toUpperCase();
    }

    /**
     * A setter without a getter is deserialized only.
     *
     * @param token The token
     */
    public void setToken(String token) {
        this.tokenSetValue = token;
    }
}
