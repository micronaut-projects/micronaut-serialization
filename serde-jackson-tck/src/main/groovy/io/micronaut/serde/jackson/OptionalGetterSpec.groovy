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

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.jackson.optionalwrap.MyThing

/**
 * Tests serialization and deserialization of beans with Optional getters
 * but plain String setters.
 */
abstract class OptionalGetterSpec extends JsonCompileSpec {

    void "test serialize and deserialize MyThing with Optional getter"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        def myThing = new MyThing()
        myThing.setValue("someValue")

        when:
        String json = jsonMapper.writeValueAsString(myThing)
        MyThing other = jsonMapper.readValue(json, MyThing)

        then:
        other.getValue().orElse(null) == "someValue"

        cleanup:
        context.close()
    }

    void "test deserialize MyThing from JSON"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        jsonMapper = context.getBean(JsonMapper)
        String json = '{"value":"myOtherValue"}'

        when:
        MyThing other = jsonMapper.readValue(json, MyThing)

        then:
        other.getValue().orElse(null) == "myOtherValue"

        cleanup:
        context.close()
    }
}
