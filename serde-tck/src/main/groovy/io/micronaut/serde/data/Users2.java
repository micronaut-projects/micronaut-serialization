package io.micronaut.serde.data;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Objects;

/**
 * Created by frenaud on 7/3/16.
 * https://github.com/fabienrenaud/java-json-benchmark/blob/master/src/main/java/com/github/fabienrenaud/jjb/model/Users.java
 */
@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class Users2 {

    public List<User> users;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Users2)) return false;

        Users2 that = (Users2) o;

        return users != null ? users.equals(that.users) : that.users == null;
    }

    @Override
    public int hashCode() {
        return users != null ? users.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Users{" + "users=" + users + '}';
    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class User {

        public String _id;
        public int index;
        public String guid;
        public boolean isActive;
        public String balance;
        public String picture;
        public int age;
        public String eyeColor;
        public String name;
        public String gender;
        public String company;
        public String email;
        public String phone;
        public String address;
        public String about;
        public String registered;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof User)) {
                return false;
            }
            User user = (User) o;
            return index == user.index &&
                isActive == user.isActive &&
                age == user.age &&
                Objects.equals(_id, user._id) &&
                Objects.equals(guid, user.guid) &&
                Objects.equals(balance, user.balance) &&
                Objects.equals(picture, user.picture) &&
                Objects.equals(eyeColor, user.eyeColor) &&
                Objects.equals(name, user.name) &&
                Objects.equals(gender, user.gender) &&
                Objects.equals(company, user.company) &&
                Objects.equals(email, user.email) &&
                Objects.equals(phone, user.phone) &&
                Objects.equals(address, user.address) &&
                Objects.equals(about, user.about) &&
                Objects.equals(registered, user.registered);
        }

        @Override
        public int hashCode() {
            return Objects.hash(_id, index, guid, isActive, balance, picture, age, eyeColor, name, gender, company, email, phone, address, about, registered);
        }
    }

}
