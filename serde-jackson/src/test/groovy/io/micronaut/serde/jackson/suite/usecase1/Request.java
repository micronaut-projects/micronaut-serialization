package io.micronaut.serde.jackson.suite.usecase1;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;

import java.time.LocalDate;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        defaultImpl = RequestUndefined.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(RequestType1.class),
        @JsonSubTypes.Type(RequestUndefined.class)
})
@Serdeable
public sealed abstract class Request permits RequestUndefined, RequestType1 {
    public static final String JSON_VALUE1 = "value1";
    public static final String JSON_VALUE2 = "value2";
    public static final String JSON_START_DATE = "start_date";
    public static final String JSON_END_DATE = "end_date";
    private String value1;
    private String value2;
    private LocalDate startDate;
    private LocalDate endDate;

    protected Request(@JsonProperty(JSON_VALUE1) String value1, @JsonProperty(JSON_VALUE2) String value2) {
        this.value1 = value1;
        this.value2 = value2;
    }

    @JsonGetter(JSON_VALUE1)
    public String getValue1() {
        return this.value1;
    }

    @JsonGetter(JSON_VALUE2)
    public String getValue2() {
        return this.value2;
    }

    @JsonGetter(JSON_START_DATE)
    public LocalDate getStartDate() {
        return this.startDate;
    }

    @JsonGetter(JSON_END_DATE)
    public LocalDate getEndDate() {
        return this.endDate;
    }

    @JsonSetter(JSON_VALUE1)
    public void setValue1(String value1) {
        this.value1 = value1;
    }

    @JsonSetter(JSON_VALUE2)
    public void setValue2(String value2) {
        this.value2 = value2;
    }

    @JsonSetter(JSON_START_DATE)
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    @JsonSetter(JSON_END_DATE)
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}
