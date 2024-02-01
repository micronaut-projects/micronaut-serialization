package io.micronaut.serde.json.stream.nodes;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

@ConfigurationProperties("example.root.config")
@Serdeable
public class RootConfig {
    private String someValue;
    private NestedConfig someNested = new NestedConfig();
    private List<NestedListConfig> someListNested = List.of();
    @MapFormat(keyFormat = StringConvention.RAW)
    private Map<String, RawNestedConfig> someRawNested = Map.of();

    public String getSomeValue() {
        return someValue;
    }

    public void setSomeValue(String someValue) {
        this.someValue = someValue;
    }

    public NestedConfig getSomeNested() {
        return someNested;
    }

    public void setSomeNested(NestedConfig someNested) {
        this.someNested = someNested;
    }

    public List<NestedListConfig> getSomeListNested() {
        return someListNested;
    }

    public void setSomeListNested(List<NestedListConfig> someListNested) {
        this.someListNested = someListNested;
    }

    public Map<String, RawNestedConfig> getSomeRawNested() {
        return someRawNested;
    }

    public void setSomeRawNested(Map<String, RawNestedConfig> someRawNested) {
        this.someRawNested = someRawNested;
    }

    @Serdeable
    @ConfigurationProperties("some-nested")
    public static class NestedConfig {
        private String nestedValue;

        public String getNestedValue() {
            return nestedValue;
        }

        public void setNestedValue(String nestedValue) {
            this.nestedValue = nestedValue;
        }
    }

    @Serdeable
    public static class NestedListConfig {
        private String nestedListValue;

        public String getNestedListValue() {
            return nestedListValue;
        }

        public void setNestedListValue(String nestedListValue) {
            this.nestedListValue = nestedListValue;
        }
    }

    @Serdeable
    public static class RawNestedConfig {
        private String rawNestedValue;

        public String getRawNestedValue() {
            return rawNestedValue;
        }

        public void setRawNestedValue(String rawNestedValue) {
            this.rawNestedValue = rawNestedValue;
        }
    }
}
