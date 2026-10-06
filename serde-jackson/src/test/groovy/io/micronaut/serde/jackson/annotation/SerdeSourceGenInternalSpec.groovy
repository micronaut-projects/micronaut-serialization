package io.micronaut.serde.jackson.annotation

import io.micronaut.core.annotation.Internal
import io.micronaut.serde.jackson.JsonCompileSpec

/**
 * Generated serdes are public classes in the package of the type, but their constructors follow the generator
 * rather than the type, so they are marked internal and binary compatibility checks do not treat them as API.
 */
class SerdeSourceGenInternalSpec extends JsonCompileSpec {

    void 'the serdes generated for a #shape are internal'() {
        given:
        def context = buildContext(className, source)

        expect:
        ['Serializer', 'Deserializer'].each { suffix ->
            Class<?> generated = context.classLoader.loadClass("test.Serde${className.substring('test.'.length())}${suffix}")
            assert generated.isAnnotationPresent(Internal)
        }

        cleanup:
        context.close()

        where:
        shape    | className     | source
        'record' | 'test.Lookup' | '''
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Lookup(String id) {
}
'''
        'bean'   | 'test.Author' | '''
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Author {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
'''
        'enum'   | 'test.Colour' | '''
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public enum Colour {
    RED,
    GREEN
}
'''
    }
}
