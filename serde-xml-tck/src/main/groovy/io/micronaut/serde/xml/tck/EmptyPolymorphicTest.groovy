package io.micronaut.serde.xml.tck

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import io.micronaut.json.JsonMapper
import io.micronaut.serde.annotation.Serdeable
import spock.lang.Specification

abstract class EmptyPolymorphicTest extends Specification{

    abstract JsonMapper getXmlMapper();


    def "Empty Polymorphic tes"(){
        given:
        def bean = new Data("Foobar");

        when:
        def xml = xmlMapper.writeValueAsString(bean)


        then:
        xml =="<Data><name>Foobar</name><proxy><EmptyProxy/></proxy></Data>"
    }




    // ============================

    @Serdeable
    @JsonTypeName(value = "lala")
    static class Data {
        public String name;

        //@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
        //@JsonSubTypes([@JsonSubTypes.Type(EmptyProxy.class)])
        public Proxy proxy;

        public Data() { }
        public Data(String n) {
            name = n;
            proxy = new EmptyProxy();
        }

        String getName() {
            return name
        }

        Proxy getProxy() {
            return proxy
        }
    }

    @Serdeable
    static interface Proxy { }

    @Serdeable
    @JsonTypeName("empty")
    static class EmptyProxy implements Proxy { }


}
