package io.micronaut.serde.jackson.builder;


@com.fasterxml.jackson.annotation.JsonTypeInfo(
    use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME,
    include = com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY,
    property = "instanceType",
    defaultImpl = InstanceConfigurationInstanceDetails.class)
@com.fasterxml.jackson.annotation.JsonSubTypes({
    @com.fasterxml.jackson.annotation.JsonSubTypes.Type(
        value = ComputeInstanceOptions.class,
        name = "instance_options"),
    @com.fasterxml.jackson.annotation.JsonSubTypes.Type(
        value = ComputeInstanceDetails.class,
        name = "compute")
})
public class InstanceConfigurationInstanceDetails {

    @java.beans.ConstructorProperties({})
    protected InstanceConfigurationInstanceDetails() {
        super();
    }
}
