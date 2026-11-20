package example;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;
import io.micronaut.serde.config.naming.UpperCamelCaseStrategy;

@Serdeable.Serializable(naming = SnakeCaseStrategy.class) // <1>
@Serdeable.Deserializable(naming = UpperCamelCaseStrategy.class) // <2>
public record DirectionalContact(String firstName, String lastName) {
}
