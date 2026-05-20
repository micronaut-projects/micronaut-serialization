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
package io.micronaut.serde.xml.tck

import com.fasterxml.jackson.annotation.JsonTypeName
import io.micronaut.serde.annotation.Serdeable
import spock.lang.Specification

abstract class AbstractXmlEmptyPolymorphicSpec extends Specification implements XmlSpec {


    def "Empty Polymorphic tes"(){
        given:
        def bean = new Data("Foobar");

        when:
        def xml = writeXml(bean)

        then:
        xml == "<Data><name>Foobar</name><proxy/></Data>"
    }

    def "Empty Polymorphic read rejects abstract proxy without type info"() {
        given:
        def xml = "<ReadableData><name>Foobar</name><proxy></proxy></ReadableData>"

        when:
        readXml(xml, ReadableData)

        then:
        thrown(Exception)
    }

    @Serdeable
    @JsonTypeName(value = "lala")
    static class Data {

        public String name;
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

    @Serdeable
    static class ReadableData {
        String name
        Proxy proxy
    }

}
