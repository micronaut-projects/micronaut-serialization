package io.micronaut.serde.xml.annotation;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
//@JsonRootName(value = "ROOT")
public class CustomBean {
    @JsonProperty("value")
    private String a1;
    private List<String> c1;
    private String b1;


    public CustomBean() {
    }

    public CustomBean(String a1, List<String> c1, String b1) {
        this.a1 = a1;
        this.c1 = c1;
        this.b1 = b1;
    }


    public String getA1() {
        return a1;
    }

    public void setA1(String a1) {
        a1 = a1;
    }

    public List<String> getC1() {
        return c1;
    }

    public void setC1(List<String> c1) {
        c1 = c1;
    }

    public String getB1() {
        return b1;
    }

    public void setB1(String b1) {
        b1 = b1;
    }
}
