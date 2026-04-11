package io.micronaut.serde.xml.annotation;

import com.fasterxml.jackson.annotation.JsonRootName;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonRootName("dsqd")
public class SimpleBean {

    int age;
    String name;

    public SimpleBean() {
    }

    public SimpleBean(int age, String name) {
        this.age = age;
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

