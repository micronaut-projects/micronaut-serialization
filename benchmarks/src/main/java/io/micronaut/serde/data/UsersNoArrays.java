package io.micronaut.serde.data;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Created by frenaud on 7/3/16.
 * https://github.com/fabienrenaud/java-json-benchmark/blob/master/src/main/java/com/github/fabienrenaud/jjb/model/Users.java
 */
@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class UsersNoArrays {

    public List<User> users;

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
        public double latitude;
        public double longitude;
        public String greeting;
        public String favoriteFruit;

    }

}
