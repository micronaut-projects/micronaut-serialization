package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.SerdeableGenerated;

import java.util.List;

/**
 * A type-level inclusion with property-level overrides for every inclusion kind.
 */
@SerdeableGenerated
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceGenIncludeOverridesBean {
    private String name;
    private List<String> items;
    private boolean flag;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String always;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String text;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> tags;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int count;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private Long id;
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    private String label;
    @JsonInclude(JsonInclude.Include.USE_DEFAULTS)
    private String configured;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items;
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public String getAlways() {
        return always;
    }

    public void setAlways(String always) {
        this.always = always;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getConfigured() {
        return configured;
    }

    public void setConfigured(String configured) {
        this.configured = configured;
    }
}
