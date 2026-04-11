package io.micronaut.serde.xml.tck;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;


@Serdeable
public class NestedList {

    private List<Object> nestedLists;

    public NestedList(List<Object> nestedLists) {
        this.nestedLists = nestedLists;
    }

    public List<Object> getNestedLists() {
        return nestedLists;
    }

}
