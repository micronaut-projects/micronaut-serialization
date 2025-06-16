/*
 * Copyright 2017-2024 original authors
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

abstract class JsonExceptionSpec extends JsonCompileSpec {

    void "enum"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    public Test2 foo;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test2 {
    public MyEnum bar;
}

enum MyEnum {
    A, B, C
}
''')

        when:
            jsonMapper.readValue('{"foo": {"bar": "xyz"}}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            getPath(e) == """example.Test["foo"]->example.Test2["bar"]"""

        cleanup:
            context.close()
    }

    void "any setter"() {
        given:
            def context = buildContext('example.Test', '''
package example;


import java.util.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
class Test {
    private Map<String, Test2> anySetter = new HashMap<>();

    @JsonAnySetter
    void put(String key, Test2 value) {
        anySetter.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> myGetter() {
        throw new IllegalStateException("Bam!");
    }

}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test2 {
    public int bar;
}
''')

        when:
            jsonMapper.readValue('{"foo": {"bar": "xyz"}}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            getPath(e) == """example.Test["foo"]->example.Test2["bar"]"""

        when:
            def bean = jsonMapper.readValue('{"foo": {"bar": 123}}}', typeUnderTest)

        then:
            bean

        when:
            jsonMapper.writeValueAsString(bean)
        then:
            def e2 = thrown(Exception)
            def path = getPath(e2)
            path == """example.Test["[anySetter]"]""" || path == """example.Test["myGetter"]"""

        cleanup:
            context.close()
    }

    void "property path"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    public Test2 foo;

    public Test2 getFoo() {
        return foo;
    }

    public void setFoo(Test2 foo) {
        this.foo = foo;
    }
}

@Serdeable
class Test2 {
    public int bar;

    public int getBar() {
        throw new IllegalStateException("Bam!");
    }

    public void setBar(int bar) {
        this.bar = bar;
    }
}
''')

        when:
            jsonMapper.readValue('{"foo": {"bar": "xyz"}}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            getPath(e) == """example.Test["foo"]->example.Test2["bar"]"""

        when:
            def bean = jsonMapper.readValue('{"foo": {"bar": 123}}}', typeUnderTest)

        then:
            bean

        when:
            jsonMapper.writeValueAsString(bean)
        then:
            def e2 = thrown(Exception)
            getPath(e2) == """example.Test["foo"]->example.Test2["bar"]"""

        cleanup:
            context.close()
    }

    void "property path set"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Set;
import java.util.LinkedHashSet;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @JsonDeserialize(as=LinkedHashSet.class)
    public Set<Test2> foo;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test2 {
    public int bar;
}
''')
        when:
            jsonMapper.readValue('{"foo": [{"bar": "xyz"}]}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            getPath(e) == """example.Test["foo"]->java.util.LinkedHashSet[0]->example.Test2["bar"]"""

        cleanup:
            context.close()
    }

    void "property path list"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
class Test {
    public List<Test2> foo;

    public List<Test2> getFoo() {
        return foo;
    }

    public void setFoo(List<Test2> foo) {
        this.foo = foo;
    }
}

@Serdeable
class Test2 {
    public int bar;

    public int getBar() {
        throw new IllegalStateException("Bam!");
    }

    public void setBar(int bar) {
        this.bar = bar;
    }
}
''')

        when:
            jsonMapper.readValue('{"foo": [{"bar": "xyz"}]}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            getPath(e) == """example.Test["foo"]->java.util.ArrayList[0]->example.Test2["bar"]"""

        when:
            def bean = jsonMapper.readValue('{"foo": [{"bar": 123}]}}', typeUnderTest)

        then:
            bean

        when:
            jsonMapper.writeValueAsString(bean)

        then:
            def ee = thrown(Exception)
            getPath(ee) == """example.Test["foo"]->java.util.ArrayList[0]->example.Test2["bar"]"""

        cleanup:
            context.close()
    }

    void "property path array"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    public Test2[] foo;

    public Test2[] getFoo() {
        return foo;
    }

    public void setFoo(Test2[] foo) {
        this.foo = foo;
    }
}

@Serdeable
class Test2 {
    public int bar;

    public int getBar() {
        throw new IllegalStateException("Bam!");
    }

    public void setBar(int bar) {
        this.bar = bar;
    }
}
''')

        when:
            jsonMapper.readValue('{"foo": [{"bar": "xyz"}]}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            def path = getPath(e)
            // Jackson always references the array as java.lang.Object[]
            path == """example.Test["foo"]->java.lang.Object[][0]->example.Test2["bar"]""" || path == """example.Test["foo"]->example.Test2[][0]->example.Test2["bar"]"""

        when:
            def bean = jsonMapper.readValue('{"foo": [{"bar": 123}]}}', typeUnderTest)

        then:
            bean

        when:
            jsonMapper.writeValueAsString(bean)

        then:
            def ee = thrown(Exception)
            getPath(ee) == """example.Test["foo"]->example.Test2[0]->example.Test2["bar"]"""

        cleanup:
            context.close()
    }

    void "property path map"() {
        given:
            def context = buildContext('example.Test', '''
package example;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
class Test {
    public Map<String, Test2> foo;

    public Map<String, Test2> getFoo() {
        return foo;
    }

    public void setFoo(Map<String, Test2> foo) {
        this.foo = foo;
    }
}

@Serdeable
class Test2 {
    public int bar;

    public int getBar() {
        throw new IllegalStateException("Bam!");
    }

    public void setBar(int bar) {
        this.bar = bar;
    }
}
''')

        when:
            jsonMapper.readValue('{"foo": {"xxx": {"bar": "xyz"}}}}', typeUnderTest)

        then:
            def e = thrown(Exception)
            getPath(e) == """example.Test["foo"]->java.util.LinkedHashMap["xxx"]->example.Test2["bar"]"""

        when:
            def bean = jsonMapper.readValue('{"foo": {"xxx": {"bar": 123}}}}', typeUnderTest)

        then:
            bean

        when:
            jsonMapper.writeValueAsString(bean)

        then:
            def ee = thrown(Exception)
            getPath(ee) == """example.Test["foo"]->java.util.LinkedHashMap["xxx"]->example.Test2["bar"]"""

        cleanup:
            context.close()
    }

    abstract String getPath(Exception e)

}
