package example;

import com.fasterxml.jackson.annotation.JsonFilter;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonFilter("person-filter") // <1>
public class Person {
    private final String name;
    private final String preferredName;

    public Person(String name, String preferredName) {
        this.name = name;
        this.preferredName = preferredName;
    }

    public String getName() {
        return name;
    }

    public String getPreferredName() {
        return preferredName;
    }
}
