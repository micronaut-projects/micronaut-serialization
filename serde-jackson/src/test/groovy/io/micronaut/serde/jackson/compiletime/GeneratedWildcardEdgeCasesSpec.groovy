package io.micronaut.serde.jackson.compiletime

import io.micronaut.serde.jackson.JsonCompileSpec

/**
 * Wildcard and bound shapes around the nested collection support of the generated serdes.
 */
class GeneratedWildcardEdgeCasesSpec extends JsonCompileSpec {

    void 'test a raw iterable whose type variable is bounded by the owning type'() {
        given:
        def context = buildContext('test.Node', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Serdeable
record Node(String name, Children children) {
}

class Children<T extends Node> implements Iterable<T> {
    final List<T> items = new ArrayList<>();

    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }
}
''')
        def nodeClass = context.classLoader.loadClass('test.Node')

        when:
        def json = jsonMapper.writeValueAsString(nodeClass.newInstance('root', null))

        then:
        json == '{"name":"root"}'

        cleanup:
        context.close()
    }

    void 'test a nested two-dimensional array behind a wildcard bound keeps its array type'() {
        given:
        def context = buildContext('test.Buckets', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Map;

@Serdeable
record Buckets(Map<String, ? extends List<String>[][]> buckets) {
}
''')

        when:
        def bean = jsonMapper.readValue('{"buckets":{"a":[[["x","y"]],[]]}}', typeUnderTest)
        def value = bean.buckets().a

        then:
        value.getClass() == List[][]
        value[0][0] == ['x', 'y']
        value[1].length == 0

        cleanup:
        context.close()
    }

    void 'test a two-dimensional array of a type without type arguments behind a wildcard bound keeps its array type'() {
        given:
        def context = buildContext('test.Grid', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
record Grid(Map<String, ? extends String[][]> cells) {
}
''')

        when:
        def bean = jsonMapper.readValue('{"cells":{"a":[["x","y"],["z"]]}}', typeUnderTest)
        def value = bean.cells().a

        then:
        value.getClass() == String[][]
        value[0] == ['x', 'y'] as String[]
        value[1] == ['z'] as String[]

        cleanup:
        context.close()
    }

    void 'test two raw iterables whose type variables share a name and lead back to the owning type'() {
        given:
        def context = buildContext('test.Node', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Serdeable
record Node(String name, Outer children) {
}

class Outer<T extends Children> implements Iterable<T> {
    final List<T> items = new ArrayList<>();

    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }
}

class Children<T extends Node> implements Iterable<T> {
    final List<T> items = new ArrayList<>();

    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }
}
''')
        def nodeClass = context.classLoader.loadClass('test.Node')

        when:
        def json = jsonMapper.writeValueAsString(nodeClass.newInstance('root', null))

        then:
        json == '{"name":"root"}'

        cleanup:
        context.close()
    }

    void 'test two raw iterables whose type variables share a name and an erased bound and lead back to the owning type'() {
        given:
        def context = buildContext('test.Node', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Serdeable
record Node(String name, Outer children) {
}

class Outer<T extends List<? extends Children>> implements Iterable<T> {
    final List<T> items = new ArrayList<>();

    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }
}

class Children<T extends List<? extends Node>> implements Iterable<T> {
    final List<T> items = new ArrayList<>();

    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }
}
''')
        def nodeClass = context.classLoader.loadClass('test.Node')

        when:
        def json = jsonMapper.writeValueAsString(nodeClass.newInstance('root', null))

        then:
        json == '{"name":"root"}'

        cleanup:
        context.close()
    }
}
