package example;

import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class ImportedMappedEntity {
    private final Long id;
    private final String name;

    public ImportedMappedEntity(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
