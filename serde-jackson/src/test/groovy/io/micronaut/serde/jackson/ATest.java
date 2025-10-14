package io.micronaut.serde.jackson;

class ATestImpl extends ATest {
    private double code;

    public void setCode(double code) {
        this.code = code;
    }

    @Override
    public double get95thPercentile() {
        return code;
    }
}

public abstract class ATest {

    public abstract double get95thPercentile();

}
