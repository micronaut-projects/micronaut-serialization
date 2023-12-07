package io.micronaut.serde.jackson.suite.usecase1;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;
import java.util.TreeMap;

@Serdeable
public final class RequestUndefined extends Request {
    Map<String, Object> values = new TreeMap<>();

    @JsonCreator
    public RequestUndefined(String value1, String value2) {
        super(value1, value2);
    }

    @JsonAnyGetter
    public Map<String, Object> getValues() {
        return values;
    }

    @JsonAnySetter
    public void setValues(String key, Object value) {
        values.put(key, value);
    }

}
