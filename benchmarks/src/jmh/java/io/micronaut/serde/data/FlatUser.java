package io.micronaut.serde.data;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.csv.annotation.CsvFormat;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
@CsvFormat
public class FlatUser {
    public Long id;
    public String name;
    public String email;
    public String phone;
    public String address;
    public String company;
    public String gender;
    public String eyeColor;
    public String balance;
    public String picture;
    public String about;
    public String registered;
    public String greeting;
    public String favoriteFruit;
    public int index;
    public int age;
    public double latitude;
    public double longitude;
    public boolean isActive;

    public FlatUser() {
    }

    public static FlatUser sample() {
        FlatUser u = new FlatUser();
        u.id = 42L;
        u.name = "Alice Smith";
        u.email = "alice@example.com";
        u.phone = "555-0100";
        u.address = "1 Main St";
        u.company = "Acme";
        u.gender = "female";
        u.eyeColor = "blue";
        u.balance = "1234.56";
        u.picture = "pic.png";
        u.about = "about me";
        u.registered = "2024-01-01";
        u.greeting = "hi";
        u.favoriteFruit = "apple";
        u.index = 7;
        u.age = 30;
        u.latitude = 13.45;
        u.longitude = 35.0;
        u.isActive = true;
        return u;
    }
}