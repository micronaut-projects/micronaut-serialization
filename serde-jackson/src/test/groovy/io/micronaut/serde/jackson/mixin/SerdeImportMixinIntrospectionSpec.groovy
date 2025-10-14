package io.micronaut.serde.jackson.mixin


import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import spock.lang.PendingFeature

class SerdeImportMixinIntrospectionSpec extends AbstractTypeElementSpec {

    void "test import field introspection"() {
        when:
            def introspection = buildBeanIntrospection('test.$io_micronaut_serde_jackson_mixin_MuxedEvent', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeImport;
import java.util.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class MuxedEventMixin {
}

@SerdeImport(mixin = MuxedEventMixin.class, value = io.micronaut.serde.jackson.mixin.MuxedEvent.class)
class Test {
}


    ''')

        then:
            introspection != null
            introspection.getBeanType().getName() == "io.micronaut.serde.jackson.mixin.MuxedEvent"
            introspection.getBeanProperties().size() == 2
            introspection.getBeanProperties().collect { it.name } == ['compartment', "content"]
    }

    @PendingFeature(reason = "includedAnnotations is not used in PropertyQuery")
    void "test import field introspection 2"() {
        when:
            def introspection = buildBeanIntrospection('test.$io_micronaut_serde_jackson_mixin_MuxedEvent2', '''
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeImport;
import java.util.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY, includedAnnotations = JsonProperty.class)
class MuxedEventMixin {
}

@SerdeImport(mixin = MuxedEventMixin.class, value = io.micronaut.serde.jackson.mixin.MuxedEvent2.class)
class Test {
}


    ''')

        then:
            introspection != null
            introspection.getBeanType().getName() == "io.micronaut.serde.jackson.mixin.MuxedEvent2"
            introspection.getBeanProperties().size() == 2
            introspection.getBeanProperties().collect { it.name } == ['compartment', "content"]
    }
}

