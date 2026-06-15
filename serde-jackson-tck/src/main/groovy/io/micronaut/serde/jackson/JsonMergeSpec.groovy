/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.jackson

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.ObjectMapper
import spock.lang.IgnoreIf

import java.nio.charset.StandardCharsets

abstract class JsonMergeSpec extends JsonCompileSpec {

    protected boolean expectsRecordLikeUpdateUnsupported() {
        true
    }

    protected boolean appliesJsonPropertyDefaultValueOnRead() {
        true
    }

    protected boolean failsOnMissingRequiredPropertyOnRead() {
        true
    }

    protected boolean preservesAbsentUnwrappedValueOnUpdate() {
        true
    }

    void "@JsonMerge nested bean updates existing value"() {
        given:
        def context = buildContext("test.MergeRoot", nestedBeanSource("MergeRoot", "@JsonMerge"))
        def root = newInstance(context, "test.MergeRoot", [:])
        def child = newInstance(context, "test.MergeRoot\$Child", [left: "keep", right: "old"])
        def patchChild = newInstance(context, "test.MergeRoot\$Child", [right: "new"])
        def patch = newInstance(context, "test.MergeRoot\$Patch", [child: patchChild])
        root.child = child

        when:
        def result = update(root, argumentOf(context, "test.MergeRoot"), patch)

        then:
        result.is(root)
        root.child.is(child)
        root.child.left == "keep"
        root.child.right == "new"

        cleanup:
        context.close()
    }

    void "nested bean without @JsonMerge replaces value"() {
        given:
        def context = buildContext("test.ReplaceRoot", nestedBeanSource("ReplaceRoot", ""))
        def root = newInstance(context, "test.ReplaceRoot", [:])
        def child = newInstance(context, "test.ReplaceRoot\$Child", [left: "old-left", right: "old-right"])
        root.child = child

        when:
        updateBytes(root, argumentOf(context, "test.ReplaceRoot"), '{"child":{"right":"new"}}')

        then:
        !root.child.is(child)
        root.child.left == null
        root.child.right == "new"

        cleanup:
        context.close()
    }

    void "@JsonMerge false keeps replacement behavior"() {
        given:
        def context = buildContext("test.MergeFalseRoot", nestedBeanSource("MergeFalseRoot", "@JsonMerge(OptBoolean.FALSE)"))
        def root = newInstance(context, "test.MergeFalseRoot", [:])
        def child = newInstance(context, "test.MergeFalseRoot\$Child", [left: "old-left", right: "old-right"])
        root.child = child

        when:
        updateStream(root, argumentOf(context, "test.MergeFalseRoot"), '{"child":{"right":"new"}}')

        then:
        !root.child.is(child)
        root.child.left == null
        root.child.right == "new"

        cleanup:
        context.close()
    }

    void "nested release window without @JsonMerge is replaced"() {
        given:
        def context = buildContext("test.ReleasePlanWithoutMerge", releasePlanSource("ReleasePlanWithoutMerge", ""))
        def releasePlan = newInstance(context, "test.ReleasePlanWithoutMerge", [
                service: "checkout",
                owner: "platform",
                rolloutWindow: newInstance(context, "test.ReleasePlanWithoutMerge\$RolloutWindow", [day: "Friday", timeZone: "UTC"])
        ])
        def rolloutWindow = releasePlan.rolloutWindow

        when:
        def result = updateBytes(releasePlan, argumentOf(context, "test.ReleasePlanWithoutMerge"),
                '{"owner":"growth","rolloutWindow":{"day":"Tuesday"}}')

        then:
        result.is(releasePlan)
        releasePlan.service == "checkout"
        releasePlan.owner == "growth"
        !releasePlan.rolloutWindow.is(rolloutWindow)
        releasePlan.rolloutWindow.day == "Tuesday"
        releasePlan.rolloutWindow.timeZone == null

        cleanup:
        context.close()
    }

    void "nested release window with @JsonMerge preserves absent fields"() {
        given:
        def context = buildContext("test.ReleasePlanWithMerge", releasePlanSource("ReleasePlanWithMerge", "@JsonMerge"))
        def releasePlan = newInstance(context, "test.ReleasePlanWithMerge", [
                service: "checkout",
                owner: "platform",
                rolloutWindow: newInstance(context, "test.ReleasePlanWithMerge\$RolloutWindow", [day: "Friday", timeZone: "UTC"])
        ])
        def rolloutWindow = releasePlan.rolloutWindow

        when:
        def result = updateStream(releasePlan, argumentOf(context, "test.ReleasePlanWithMerge"),
                '{"owner":"growth","rolloutWindow":{"day":"Tuesday"}}')

        then:
        result.is(releasePlan)
        releasePlan.service == "checkout"
        releasePlan.owner == "growth"
        releasePlan.rolloutWindow.is(rolloutWindow)
        releasePlan.rolloutWindow.day == "Tuesday"
        releasePlan.rolloutWindow.timeZone == "UTC"

        cleanup:
        context.close()
    }

    void "@JsonMerge map updates existing keys and adds new keys"() {
        given:
        def context = buildContext("test.MergeMapRoot", """
package test;

import com.fasterxml.jackson.annotation.JsonMerge;
import io.micronaut.serde.annotation.Serdeable;
import java.util.LinkedHashMap;
import java.util.Map;

@Serdeable
public class MergeMapRoot {
    @JsonMerge
    private Map<String, Child> children = new LinkedHashMap<>();

    public Map<String, Child> getChildren() {
        return children;
    }

    public void setChildren(Map<String, Child> children) {
        this.children = children;
    }

    @Serdeable
    public static class Child {
        private String left;
        private String right;

        public String getLeft() {
            return left;
        }

        public void setLeft(String left) {
            this.left = left;
        }

        public String getRight() {
            return right;
        }

        public void setRight(String right) {
            this.right = right;
        }
    }
}
""")
        def root = newInstance(context, "test.MergeMapRoot", [:])
        def children = root.children
        def existing = newInstance(context, "test.MergeMapRoot\$Child", [left: "keep", right: "old"])
        children.one = existing

        when:
        update(root, argumentOf(context, "test.MergeMapRoot"), [children: [one: [right: "new"], two: [left: "added"]]])

        then:
        root.children.is(children)
        root.children.one.is(existing)
        root.children.one.left == "keep"
        root.children.one.right == "new"
        root.children.two.left == "added"

        cleanup:
        context.close()
    }

    void "@JsonMerge labels map keeps existing entries"() {
        given:
        def context = buildContext("test.MergeLabelsRoot", """
package test;

import com.fasterxml.jackson.annotation.JsonMerge;
import io.micronaut.serde.annotation.Serdeable;
import java.util.LinkedHashMap;
import java.util.Map;

@Serdeable
public class MergeLabelsRoot {
    private String service;

    @JsonMerge
    private Map<String, String> labels = new LinkedHashMap<>();

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }
}
""")
        def root = newInstance(context, "test.MergeLabelsRoot", [service: "checkout"])
        def labels = root.labels
        labels.environment = "production"

        when:
        def result = updateBuffer(root, argumentOf(context, "test.MergeLabelsRoot"),
                '{"labels":{"version":"2026.06","region":"eu-west"}}')

        then:
        result.is(root)
        root.service == "checkout"
        root.labels.is(labels)
        root.labels == [environment: "production", version: "2026.06", region: "eu-west"]

        cleanup:
        context.close()
    }

    void "@JsonMerge collection appends incoming values"() {
        given:
        def context = buildContext("test.MergeListRoot", """
package test;

import com.fasterxml.jackson.annotation.JsonMerge;
import io.micronaut.serde.annotation.Serdeable;
import java.util.ArrayList;
import java.util.List;

@Serdeable
public class MergeListRoot {
    @JsonMerge
    private List<String> values = new ArrayList<>();

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }
}
""")
        def root = newInstance(context, "test.MergeListRoot", [:])
        def values = root.values
        values.add("a")

        when:
        updateBuffer(root, argumentOf(context, "test.MergeListRoot"), '{"values":["b","c"]}')

        then:
        root.values.is(values)
        root.values == ["a", "b", "c"]

        cleanup:
        context.close()
    }

    void "@JsonMerge array appends incoming array values"() {
        given:
        def context = buildContext("test.MergeArrayRoot", """
package test;

import com.fasterxml.jackson.annotation.JsonMerge;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class MergeArrayRoot {
    @JsonMerge
    private String[] values;

    public String[] getValues() {
        return values;
    }

    public void setValues(String[] values) {
        this.values = values;
    }
}
""")
        def root = newInstance(context, "test.MergeArrayRoot", [values: ["a"] as String[]])
        def values = root.values

        when:
        update(root, argumentOf(context, "test.MergeArrayRoot"), [values: ["b", "c"]])

        then:
        !root.values.is(values)
        root.values as List == ["a", "b", "c"]

        cleanup:
        context.close()
    }

    void "updateValueFromTree mutable bean updates present fields only"() {
        given:
        def context = buildContext("test.GeneratedUpdateRoot", """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class GeneratedUpdateRoot {
    private String changed;
    private String unchanged;

    public String getChanged() {
        return changed;
    }

    public void setChanged(String changed) {
        this.changed = changed;
    }

    public String getUnchanged() {
        return unchanged;
    }

    public void setUnchanged(String unchanged) {
        this.unchanged = unchanged;
    }
}
""")
        def root = newInstance(context, "test.GeneratedUpdateRoot", [changed: "old", unchanged: "keep"])

        when:
        jsonMapper.updateValueFromTree(root, JsonNode.from([changed: "new"]))

        then:
        root.changed == "new"
        root.unchanged == "keep"

        cleanup:
        context.close()
    }

    @IgnoreIf({ !instance.appliesJsonPropertyDefaultValueOnRead() })
    void "readValue still applies property defaults for new simple beans"() {
        given:
        def context = buildContext("test.UpdateDefaultsRoot", updateDefaultsRootSource())

        when:
        def root = jsonMapper.readValue('{"required":"present"}', argumentOf(context, "test.UpdateDefaultsRoot"))

        then:
        root.defaulted == "from-default"
        root.defaultedPrimitive == 33
        root.required == "present"

        cleanup:
        context.close()
    }

    @IgnoreIf({ !instance.failsOnMissingRequiredPropertyOnRead() })
    void "readValue still applies required checks for new simple beans"() {
        given:
        def context = buildContext("test.UpdateDefaultsRoot", updateDefaultsRootSource())

        when:
        jsonMapper.readValue('{}', argumentOf(context, "test.UpdateDefaultsRoot"))

        then:
        def e = thrown(Exception)
        e.message.contains("Required property") || e.message.contains("required")

        cleanup:
        context.close()
    }

    void "updateValue skips applyDefaults for absent simple bean properties with #mode input"() {
        given:
        def context = buildContext("test.UpdateDefaultsRoot", updateDefaultsRootSource())
        def root = newInstance(context, "test.UpdateDefaultsRoot", [
                changed: "old",
                defaulted: "keep-default",
                required: "keep-required",
                defaultedPrimitive: 9
        ])

        when:
        def result = updateByMode(mode, root, argumentOf(context, "test.UpdateDefaultsRoot"), '{"changed":"new"}')

        then:
        result.is(root)
        root.changed == "new"
        root.defaulted == "keep-default"
        root.required == "keep-required"
        root.defaultedPrimitive == 9

        cleanup:
        context.close()

        where:
        mode << ["object", "bytes", "stream", "buffer", "tree"]
    }

    @IgnoreIf({ !instance.appliesJsonPropertyDefaultValueOnRead() })
    void "readValue still applies unwrapped defaults for new specific beans"() {
        given:
        def context = buildContext("test.UpdateUnwrappedDefaultsRoot", updateUnwrappedDefaultsRootSource())

        when:
        def root = jsonMapper.readValue('{}', argumentOf(context, "test.UpdateUnwrappedDefaultsRoot"))

        then:
        root.name != null
        root.name.first == "from-unwrapped-default"
        root.name.last == "from-unwrapped-last"

        cleanup:
        context.close()
    }

    @IgnoreIf({ !instance.preservesAbsentUnwrappedValueOnUpdate() })
    void "updateValue skips applyDefaults for absent unwrapped properties with #mode input"() {
        given:
        def context = buildContext("test.UpdateUnwrappedDefaultsRoot", updateUnwrappedDefaultsRootSource())
        def name = newInstance(context, "test.UpdateUnwrappedDefaultsRoot\$Name", [
                first: "keep-first",
                last: "keep-last"
        ])
        def root = newInstance(context, "test.UpdateUnwrappedDefaultsRoot", [
                changed: "old",
                name: name
        ])

        when:
        def result = updateByMode(mode, root, argumentOf(context, "test.UpdateUnwrappedDefaultsRoot"), '{"changed":"new"}')

        then:
        result.is(root)
        root.changed == "new"
        root.name.is(name)
        root.name.first == "keep-first"
        root.name.last == "keep-last"

        cleanup:
        context.close()

        where:
        mode << ["object", "bytes", "stream", "buffer", "tree"]
    }

    @IgnoreIf({ !instance.expectsRecordLikeUpdateUnsupported() })
    void "record-like update is unsupported"() {
        given:
        def context = buildContext("test.ImmutableRoot", """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ImmutableRoot(String name) {
}
""")
        def root = newInstance(context, "test.ImmutableRoot", ["old"] as Object[])

        when:
        update(root, argumentOf(context, "test.ImmutableRoot"), [name: "new"])

        then:
        def e = thrown(Exception)
        e.message.contains("Unsupported") || e.message.contains("Cannot update")

        cleanup:
        context.close()
    }

    protected Object update(Object value, Argument type, Object overrides) {
        if (jsonMapper instanceof ObjectMapper) {
            return ((ObjectMapper) jsonMapper).updateValue(value, type, overrides)
        }
        JsonNode tree = overrides instanceof JsonNode ? (JsonNode) overrides : jsonMapper.writeValueToTree(overrides)
        jsonMapper.updateValueFromTree(value, tree)
        return value
    }

    protected Object updateBytes(Object value, Argument type, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8)
        if (jsonMapper instanceof ObjectMapper) {
            return ((ObjectMapper) jsonMapper).updateValue(value, type, bytes)
        }
        jsonMapper.updateValueFromTree(value, jsonMapper.readValue(bytes, JsonNode))
        return value
    }

    protected Object updateStream(Object value, Argument type, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8)
        if (jsonMapper instanceof ObjectMapper) {
            return ((ObjectMapper) jsonMapper).updateValue(value, type, new ByteArrayInputStream(bytes))
        }
        jsonMapper.updateValueFromTree(value, jsonMapper.readValue(bytes, JsonNode))
        return value
    }

    protected Object updateBuffer(Object value, Argument type, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8)
        if (jsonMapper instanceof ObjectMapper) {
            return ((ObjectMapper) jsonMapper).updateValue(value, type, ByteArrayBufferFactory.INSTANCE.wrap(bytes))
        }
        jsonMapper.updateValueFromTree(value, jsonMapper.readValue(bytes, JsonNode))
        return value
    }

    private Object updateByMode(String mode, Object value, Argument type, String json) {
        switch (mode) {
            case "object":
                return update(value, type, jsonMapper.readValue(json, Map))
            case "bytes":
                return updateBytes(value, type, json)
            case "stream":
                return updateStream(value, type, json)
            case "buffer":
                return updateBuffer(value, type, json)
            case "tree":
                jsonMapper.updateValueFromTree(value, jsonMapper.readValue(json, JsonNode))
                return value
            default:
                throw new IllegalArgumentException("Unknown update mode: " + mode)
        }
    }

    private static String releasePlanSource(String rootName, String mergeAnnotation) {
        """
package test;

import com.fasterxml.jackson.annotation.JsonMerge;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class ${rootName} {
    private String service;
    private String owner;

    ${mergeAnnotation}
    private RolloutWindow rolloutWindow;

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public RolloutWindow getRolloutWindow() {
        return rolloutWindow;
    }

    public void setRolloutWindow(RolloutWindow rolloutWindow) {
        this.rolloutWindow = rolloutWindow;
    }

    @Serdeable
    public static class RolloutWindow {
        private String day;
        private String timeZone;

        public String getDay() {
            return day;
        }

        public void setDay(String day) {
            this.day = day;
        }

        public String getTimeZone() {
            return timeZone;
        }

        public void setTimeZone(String timeZone) {
            this.timeZone = timeZone;
        }
    }
}
"""
    }

    private static String nestedBeanSource(String rootName, String mergeAnnotation) {
        """
package test;

import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.OptBoolean;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class ${rootName} {
    ${mergeAnnotation}
    private Child child;

    public Child getChild() {
        return child;
    }

    public void setChild(Child child) {
        this.child = child;
    }

    @Serdeable
    public static class Patch {
        private Child child;

        public Child getChild() {
            return child;
        }

        public void setChild(Child child) {
            this.child = child;
        }
    }

    @Serdeable
    public static class Child {
        private String left;
        private String right;

        public String getLeft() {
            return left;
        }

        public void setLeft(String left) {
            this.left = left;
        }

        public String getRight() {
            return right;
        }

        public void setRight(String right) {
            this.right = right;
        }
    }
}
"""
    }

    private static String updateDefaultsRootSource() {
        """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class UpdateDefaultsRoot {
    private String changed;

    @JsonProperty(defaultValue = "from-default")
    private String defaulted;

    @JsonProperty(required = true)
    private String required;

    @JsonProperty(defaultValue = "33")
    private int defaultedPrimitive;

    public String getChanged() {
        return changed;
    }

    public void setChanged(String changed) {
        this.changed = changed;
    }

    public String getDefaulted() {
        return defaulted;
    }

    public void setDefaulted(String defaulted) {
        this.defaulted = defaulted;
    }

    public String getRequired() {
        return required;
    }

    public void setRequired(String required) {
        this.required = required;
    }

    public int getDefaultedPrimitive() {
        return defaultedPrimitive;
    }

    public void setDefaultedPrimitive(int defaultedPrimitive) {
        this.defaultedPrimitive = defaultedPrimitive;
    }
}
"""
    }

    private static String updateUnwrappedDefaultsRootSource() {
        """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class UpdateUnwrappedDefaultsRoot {
    private String changed;

    @JsonUnwrapped
    private Name name;

    public String getChanged() {
        return changed;
    }

    public void setChanged(String changed) {
        this.changed = changed;
    }

    public Name getName() {
        return name;
    }

    public void setName(Name name) {
        this.name = name;
    }

    @Serdeable
    public static class Name {
        @JsonProperty(defaultValue = "from-unwrapped-default")
        private String first;

        @JsonProperty(defaultValue = "from-unwrapped-last")
        private String last;

        public String getFirst() {
            return first;
        }

        public void setFirst(String first) {
            this.first = first;
        }

        public String getLast() {
            return last;
        }

        public void setLast(String last) {
            this.last = last;
        }
    }
}
"""
    }
}
