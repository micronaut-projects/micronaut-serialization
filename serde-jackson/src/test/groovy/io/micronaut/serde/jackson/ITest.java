package io.micronaut.serde.jackson;

class ITestImpl implements ITest {
    private double code;

    public void setCode(double code) {
        this.code = code;
    }

    @Override
    public double get95thPercentile() {
        return code;
    }
}

public interface ITest {

    double get95thPercentile();

}
