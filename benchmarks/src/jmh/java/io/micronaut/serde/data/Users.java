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
public class Users {

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
        public List<String> tags;
        public List<Friend> friends;
        public String greeting;
        public String favoriteFruit;

    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class Friend {

        public String id;
        public String name;

        public Friend() {
        }

        public static Friend create(String id, String name) {
            Friend friend = new Friend();
            friend.id = id;
            friend.name = name;
            return friend;
        }

    }

}
