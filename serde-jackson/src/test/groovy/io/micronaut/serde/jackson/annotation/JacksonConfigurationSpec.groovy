package io.micronaut.serde.jackson.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.json.JsonMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.jackson.JsonCompileSpec

class JacksonConfigurationSpec extends JsonCompileSpec {

    void "test pretty print"() {
        given:
        def context = ApplicationContext.run([
                "micronaut.serde.jackson.pretty-print" : "true"
        ])

        when:
        def result = context.getBean(JsonMapper).writeValueAsString(new MyUser(firstName: "Joe", lastName: "Doe", age: 33))
                // Fix for windows
                .replaceAll("\r\n", "\n")

        then:
        result == """{
  "firstName" : "Joe",
  "lastName" : "Doe",
  "age" : 33
}"""

        cleanup:
        context.close()
    }

    void "test write features"() {
        given:
        def context = ApplicationContext.run([
                "micronaut.serde.jackson.write-features.QUOTE_FIELD_NAMES" : "false"
        ])

        when:
        def result = context.getBean(JsonMapper).writeValueAsString(new MyUser(firstName: "Joe", lastName: "Doe", age: 33))

        then:
        result == '{firstName:"Joe",lastName:"Doe",age:33}'

        cleanup:
        context.close()
    }

    void "test generator features"() {
        given:
        def context = ApplicationContext.run([
                "micronaut.serde.jackson.generator-features.QUOTE_FIELD_NAMES" : "false"
        ])

        when:
        def result = context.getBean(JsonMapper).writeValueAsString(new MyUser(firstName: "Joe", lastName: "Doe", age: 33))

        then:
        result == '{firstName:"Joe",lastName:"Doe",age:33}'

        cleanup:
        context.close()
    }

    void "test read features"() {
        given:
        def context = ApplicationContext.run([
                "micronaut.serde.jackson.read-features.ALLOW_UNQUOTED_FIELD_NAMES" : "true"
        ])

        when:
        def result = context.getBean(JsonMapper).readValue('{firstName:"Joe",lastName:"Doe",age:33}', MyUser)

        then:
        result.firstName == "Joe"
        result.lastName == "Doe"
        result.age == 33

        cleanup:
        context.close()
    }

    void "test parser features"() {
        given:
        def context = ApplicationContext.run([
                "micronaut.serde.jackson.parser-features.ALLOW_UNQUOTED_FIELD_NAMES" : "true"
        ])

        when:
        def result = context.getBean(JsonMapper).readValue('{firstName:"Joe",lastName:"Doe",age:33}', MyUser)

        then:
        result.firstName == "Joe"
        result.lastName == "Doe"
        result.age == 33

        cleanup:
        context.close()
    }

    @Serdeable
    static class MyUser {

        private String firstName
        private String lastName
        private int age

        String getFirstName() {
            return firstName
        }

        void setFirstName(String firstName) {
            this.firstName = firstName
        }

        String getLastName() {
            return lastName
        }

        void setLastName(String lastName) {
            this.lastName = lastName
        }

        int getAge() {
            return age
        }

        void setAge(int age) {
            this.age = age
        }
    }
}
