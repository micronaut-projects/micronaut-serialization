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

import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

abstract class JsonAutoDetectSpec extends JsonCompileSpec {

    void "JsonAutoDetect NONE allows explicit JsonProperty JsonGetter and JsonSetter"() {
        given:
            def context = buildContext('''
package jsonautodetect;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE
)
class Test {
    @JsonProperty("field_value")
    public String fieldValue = "field-default";
    public String plainField = "plain-default";
    public String getterValue = "getter-default";
    public String setterValue = "setter-default";

    @JsonGetter("getter_value")
    public String getterValue() {
        return getterValue;
    }

    @JsonSetter("setter_value")
    public void setterValue(String setterValue) {
        this.setterValue = setterValue;
    }

    public String getPlainGetter() {
        return "plain-getter";
    }

    public void setPlainSetter(String plainSetter) {
        this.setterValue = plainSetter;
    }
}
''')
            def argument = argumentOf(context, 'jsonautodetect.Test')

        when:
            def bean = newInstance(context, 'jsonautodetect.Test', [
                fieldValue: 'field-out',
                plainField: 'plain-out',
                getterValue: 'getter-out',
                setterValue: 'setter-out'
            ])
            def result = writeJson(jsonMapper, bean)

        then:
            JSONAssert.assertEquals('{"field_value":"field-out","getter_value":"getter-out"}', result, JSONCompareMode.NON_EXTENSIBLE)

        when:
            bean = jsonMapper.readValue('{"field_value":"field-in","setter_value":"setter-in"}', argument)

        then:
            bean.fieldValue == 'field-in'
            bean.plainField == 'plain-default'
            bean.getterValue == 'getter-default'
            bean.setterValue == 'setter-in'

        cleanup:
            context.close()
    }

    void "JsonAutoDetect public accessors use explicit JsonProperty JsonGetter and JsonSetter names"() {
        given:
            def context = buildContext('''
package jsonautodetect;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    setterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY
)
class Test {
    private String autoValue = "auto-default";
    private String renamedValue = "renamed-default";
    private String getterOnly = "getter-default";
    private String setterOnly = "setter-default";
    private String hiddenValue = "hidden-default";

    public String getAutoValue() {
        return autoValue;
    }

    public void setAutoValue(String autoValue) {
        this.autoValue = autoValue;
    }

    @JsonProperty("renamed")
    public String getRenamedValue() {
        return renamedValue;
    }

    @JsonProperty("renamed")
    public void setRenamedValue(String renamedValue) {
        this.renamedValue = renamedValue;
    }

    @JsonGetter("getter_only")
    public String getterOnly() {
        return getterOnly;
    }

    @JsonSetter("setter_only")
    public void setterOnly(String setterOnly) {
        this.setterOnly = setterOnly;
    }

    String getHiddenValue() {
        return hiddenValue;
    }

    void setHiddenValue(String hiddenValue) {
        this.hiddenValue = hiddenValue;
    }

    public String getSetterOnlyValue() {
        return setterOnly;
    }
}
''')
            def argument = argumentOf(context, 'jsonautodetect.Test')

        when:
            def bean = newInstance(context, 'jsonautodetect.Test', [
                autoValue: 'auto-out',
                renamedValue: 'renamed-out',
                getterOnly: 'getter-out',
                setterOnly: 'setter-out',
                hiddenValue: 'hidden-out'
            ])
            def result = writeJson(jsonMapper, bean)

        then:
            JSONAssert.assertEquals('{"autoValue":"auto-out","renamed":"renamed-out","getter_only":"getter-out","setterOnlyValue":"setter-out"}', result, JSONCompareMode.NON_EXTENSIBLE)

        when:
            bean = jsonMapper.readValue('{"autoValue":"auto-in","renamed":"renamed-in","setter_only":"setter-in"}', argument)

        then:
            bean.autoValue == 'auto-in'
            bean.renamedValue == 'renamed-in'
            bean.getterOnly == 'getter-default'
            bean.setterOnlyValue == 'setter-in'
            bean.hiddenValue == 'hidden-default'

        cleanup:
            context.close()
    }
}
