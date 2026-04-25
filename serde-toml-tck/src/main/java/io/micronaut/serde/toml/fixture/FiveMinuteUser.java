package io.micronaut.serde.toml.fixture;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Arrays;
import java.util.Objects;

@Serdeable
@JsonPropertyOrder({"firstName", "lastName", "gender", "verified", "userImage"})
public class FiveMinuteUser {
    public enum Gender {
        MALE,
        FEMALE
    }

    private String firstName;
    private String lastName;
    private Gender gender;
    private boolean verified;
    private byte[] userImage;

    public FiveMinuteUser() {
    }

    public FiveMinuteUser(String firstName, String lastName, Gender gender, boolean verified, byte[] userImage) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.gender = gender;
        this.verified = verified;
        this.userImage = userImage;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public byte[] getUserImage() {
        return userImage;
    }

    public void setUserImage(byte[] userImage) {
        this.userImage = userImage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FiveMinuteUser that)) {
            return false;
        }
        return verified == that.verified
            && Objects.equals(firstName, that.firstName)
            && Objects.equals(lastName, that.lastName)
            && gender == that.gender
            && Arrays.equals(userImage, that.userImage);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(firstName, lastName, gender, verified);
        result = 31 * result + Arrays.hashCode(userImage);
        return result;
    }
}
