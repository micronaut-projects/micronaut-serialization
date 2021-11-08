package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Objects;

@Serdeable
final class ObjectWithArrayOfArray {

    private List<List<SomeObject>> vals;

    public List<List<SomeObject>> getVals() {
        return vals;
    }

    public void setVals(List<List<SomeObject>> vals) {
        this.vals = vals;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectWithArrayOfArray that = (ObjectWithArrayOfArray) o;
        return Objects.equals(vals, that.vals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vals);
    }
}
