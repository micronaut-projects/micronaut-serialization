package io.micronaut.serde.xml.annotation;

import com.fasterxml.jackson.annotation.JsonRootName;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class ObjectBean {

    String name;
    SimpleBean simpleBeansA;
    int[] age;


    public ObjectBean() {
    }

    public ObjectBean(int[] age, SimpleBean simpleBean,  String name) {
        this.age = age;
        this.simpleBeansA = simpleBean;
        this.name = name;
    }

    public int[] getAge() {
        return age;
    }

    public SimpleBean getSimpleBeansA() {
        return simpleBeansA;
    }

    public String getName() {
        return name;
    }
}
