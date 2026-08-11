package io.micronaut.serde.xml.bean;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class SimpleBean {

    String name;
    int age;

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
