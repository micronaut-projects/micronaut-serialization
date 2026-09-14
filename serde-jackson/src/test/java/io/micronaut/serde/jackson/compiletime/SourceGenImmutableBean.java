package io.micronaut.serde.jackson.compiletime;

import io.micronaut.serde.annotation.SerdeableGenerated;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An immutable class bound through its constructor, generated like a record.
 */
@SerdeableGenerated
public final class SourceGenImmutableBean {
    private final String name;
    private final int count;
    private final @Nullable String note;
    private final List<String> tags;

    public SourceGenImmutableBean(String name, int count, @Nullable String note, List<String> tags) {
        this.name = name;
        this.count = count;
        this.note = note;
        this.tags = tags;
    }

    public String getName() {
        return name;
    }

    public int getCount() {
        return count;
    }

    public @Nullable String getNote() {
        return note;
    }

    public List<String> getTags() {
        return tags;
    }
}
