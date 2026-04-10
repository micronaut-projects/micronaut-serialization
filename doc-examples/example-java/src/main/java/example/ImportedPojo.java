package example;

public class ImportedPojo {
    private final Long id;
    private final String name;

    public ImportedPojo(Long id, String name) {
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
