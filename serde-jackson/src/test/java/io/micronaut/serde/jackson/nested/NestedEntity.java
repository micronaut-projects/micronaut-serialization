package io.micronaut.serde.jackson.nested;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class NestedEntity {

    @JsonUnwrapped(prefix = "hk_")
    private NestedEntityId hashKey;

    private String value;

    @JsonUnwrapped(prefix = "addr_")
    private Address address;

    @JsonUnwrapped
    private Audit audit = new Audit();

    public NestedEntityId getHashKey() {
        return hashKey;
    }

    public void setHashKey(NestedEntityId hashKey) {
        this.hashKey = hashKey;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit;
    }

}
