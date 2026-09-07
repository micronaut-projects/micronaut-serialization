package io.micronaut.serde.jackson.annotation

import io.micronaut.serde.jackson.JsonCompileSpec

class SerdeJsonIdentityInfoSpec extends JsonCompileSpec {

    void "JsonIdentityInfo rejects generators other than PropertyGenerator"() {
        when:
        buildContext('''
package identityerror;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "id")
class Person {
    public int id;
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("JsonIdentityInfo only supports ObjectIdGenerators.PropertyGenerator")
    }

    void "JsonIdentityInfo rejects a custom scope"() {
        when:
        buildContext('''
package identityerror;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id", scope = Person.class)
class Person {
    public int id;
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("JsonIdentityInfo member [scope] is not supported")
    }

    void "JsonIdentityInfo rejects an unknown identity property"() {
        when:
        buildContext('''
package identityerror;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "key")
class Person {
    public int id;
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("JsonIdentityInfo property [key] does not match a bean property")
    }

    void "JsonIdentityInfo is rejected on a property"() {
        when:
        buildContext('''
package identityerror;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Team {
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public Person manager;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Person {
    public int id;
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Annotation @JsonIdentityInfo is only supported on types")
    }

    void "JsonIdentityReference requires a type with JsonIdentityInfo"() {
        when:
        buildContext('''
package identityerror;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Team {
    @JsonIdentityReference(alwaysAsId = true)
    public Person manager;
}

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Person {
    public int id;
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("JsonIdentityReference requires a property type annotated with JsonIdentityInfo")
    }
}
