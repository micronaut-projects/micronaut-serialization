package io.micronaut.serde.toml

import io.micronaut.core.type.Argument
import io.micronaut.serde.Encoder
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.toml.encodestyle.InlineRootEncoder
import io.micronaut.serde.toml.encodestyle.TableRootEncoder
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class TomlManualEncoderDecoderSpec extends Specification {

    void "manual table encoder produces flat scalar object"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new TableRootEncoder(output, LimitingStream.DEFAULT_LIMITS, false)

        when:
        def object = encoder.encodeObject(Argument.of(Map))
        object.encodeKey('name')
        object.encodeString('Bob')
        object.encodeKey('age')
        object.encodeInt(42)
        object.encodeKey('active')
        object.encodeBoolean(true)
        object.finishStructure()
        encoder.writeCompleted()

        then:
        output.toString(StandardCharsets.UTF_8) ==
"""name = 'Bob'
age = 42
active = true
"""
    }

    void "manual inline encoder produces flat scalar object"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new InlineRootEncoder(output, LimitingStream.DEFAULT_LIMITS, false)

        when:
        def object = encoder.encodeObject(Argument.of(Map))
        object.encodeKey('name')
        object.encodeString('Bob')
        object.encodeKey('age')
        object.encodeInt(42)
        object.finishStructure()
        encoder.writeCompleted()

        then:
        output.toString(StandardCharsets.UTF_8) ==
"""name = 'Bob'
age = 42
"""
    }

    void "manual table encoder produces nested object as table header"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new TableRootEncoder(output, LimitingStream.DEFAULT_LIMITS, false)

        when:
        def root = encoder.encodeObject(Argument.of(Map))
        root.encodeKey('title')
        root.encodeString('Micronaut in Action')
        root.encodeKey('pages')
        root.encodeInt(320)
        root.encodeKey('author')
        def author = root.encodeObject(Argument.of(Map))
        author.encodeKey('name')
        author.encodeString('Ada')
        author.finishStructure()
        root.finishStructure()
        encoder.writeCompleted()

        then:
        output.toString(StandardCharsets.UTF_8) ==
"""title = 'Micronaut in Action'
pages = 320

[author]
name = 'Ada'
"""
    }

    void "manual inline encoder produces nested object as inline table"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new InlineRootEncoder(output, LimitingStream.DEFAULT_LIMITS, false)

        when:
        def root = encoder.encodeObject(Argument.of(Map))
        root.encodeKey('title')
        root.encodeString('Micronaut in Action')
        root.encodeKey('author')
        def author = root.encodeObject(Argument.of(Map))
        author.encodeKey('name')
        author.encodeString('Ada')
        author.finishStructure()
        root.finishStructure()
        encoder.writeCompleted()

        then:
        output.toString(StandardCharsets.UTF_8) ==
"""title = 'Micronaut in Action'
author = {name = 'Ada'}
"""
    }

    void "manual table encoder produces array of tables"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new TableRootEncoder(output, LimitingStream.DEFAULT_LIMITS, false)

        when:
        def root = encoder.encodeObject(Argument.of(Map))
        root.encodeKey('products')
        def array = root.encodeArray(Argument.of(List))
        def p1 = array.encodeObject(Argument.of(Map))
        p1.encodeKey('name')
        p1.encodeString('Hammer')
        p1.encodeKey('sku')
        p1.encodeInt(738592)
        p1.finishStructure()
        def p2 = array.encodeObject(Argument.of(Map))
        p2.encodeKey('name')
        p2.encodeString('Nail')
        p2.encodeKey('sku')
        p2.encodeInt(284758)
        p2.finishStructure()
        array.finishStructure()
        root.finishStructure()
        encoder.writeCompleted()

        then:
        output.toString(StandardCharsets.UTF_8) ==
"""[[products]]
name = 'Hammer'
sku = 738592

[[products]]
name = 'Nail'
sku = 284758
"""
    }

    void "manual inline encoder produces array of inline tables"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new InlineRootEncoder(output, LimitingStream.DEFAULT_LIMITS, false)

        when:
        def root = encoder.encodeObject(Argument.of(Map))
        root.encodeKey('products')
        def array = root.encodeArray(Argument.of(List))
        def p1 = array.encodeObject(Argument.of(Map))
        p1.encodeKey('name')
        p1.encodeString('Hammer')
        p1.finishStructure()
        def p2 = array.encodeObject(Argument.of(Map))
        p2.encodeKey('name')
        p2.encodeString('Nail')
        p2.finishStructure()
        array.finishStructure()
        root.finishStructure()
        encoder.writeCompleted()

        then:
        output.toString(StandardCharsets.UTF_8) ==
"""products = [{name = 'Hammer'}, {name = 'Nail'}]
"""
    }

    void "manual encoder handles all scalar types"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new TableRootEncoder(output, LimitingStream.DEFAULT_LIMITS, false)

        when:
        def root = encoder.encodeObject(Argument.of(Map))
        root.encodeKey('str')
        root.encodeString('hello')
        root.encodeKey('bool')
        root.encodeBoolean(false)
        root.encodeKey('int_val')
        root.encodeInt(42)
        root.encodeKey('long_val')
        root.encodeLong(9999999999L)
        root.encodeKey('float_val')
        root.encodeFloat(3.14f)
        root.encodeKey('double_val')
        root.encodeDouble(2.718d)
        root.encodeKey('binary')
        root.encodeBinary([1, 2, 3] as byte[])
        root.finishStructure()
        encoder.writeCompleted()

        then:
        output.toString(StandardCharsets.UTF_8) ==
"""str = 'hello'
bool = false
int_val = 42
long_val = 9999999999
float_val = 3.14
double_val = 2.718
binary = 'AQID'
"""
    }

    void "manual encoder encodes null as empty string sentinel"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new TableRootEncoder(output, LimitingStream.DEFAULT_LIMITS, false)

        when:
        def root = encoder.encodeObject(Argument.of(Map))
        root.encodeKey('value')
        root.encodeNull()
        root.finishStructure()
        encoder.writeCompleted()

        then:
        output.toString(StandardCharsets.UTF_8) == "value = ''\n"
    }

    void "manual encoder rejects null when fail-on-null-write is enabled"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new TableRootEncoder(output, LimitingStream.DEFAULT_LIMITS, true)

        when:
        def root = encoder.encodeObject(Argument.of(Map))
        root.encodeKey('value')
        root.encodeNull()

        then:
        thrown(Exception)
    }

    void "manual encoder produces scalar array"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new TableRootEncoder(output, LimitingStream.DEFAULT_LIMITS, false)

        when:
        def root = encoder.encodeObject(Argument.of(Map))
        root.encodeKey('ports')
        def array = root.encodeArray(Argument.of(List))
        array.encodeInt(8080)
        array.encodeInt(8081)
        array.encodeInt(8082)
        array.finishStructure()
        root.finishStructure()
        encoder.writeCompleted()

        then:
        output.toString(StandardCharsets.UTF_8) == "ports = [8080, 8081, 8082]\n"
    }

    void "manual encoder produces deeply nested table with sub-array"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new TableRootEncoder(output, LimitingStream.DEFAULT_LIMITS, false)

        when:
        def root = encoder.encodeObject(Argument.of(Map))
        root.encodeKey('products')
        def products = root.encodeArray(Argument.of(List))
        def product = products.encodeObject(Argument.of(Map))
        product.encodeKey('name')
        product.encodeString('Hammer')
        product.encodeKey('details')
        def details = product.encodeObject(Argument.of(Map))
        details.encodeKey('weight')
        details.encodeInt(500)
        details.finishStructure()
        product.encodeKey('variants')
        def variants = product.encodeArray(Argument.of(List))
        def v1 = variants.encodeObject(Argument.of(Map))
        v1.encodeKey('color')
        v1.encodeString('red')
        v1.finishStructure()
        def v2 = variants.encodeObject(Argument.of(Map))
        v2.encodeKey('color')
        v2.encodeString('blue')
        v2.finishStructure()
        variants.finishStructure()
        product.finishStructure()
        products.finishStructure()
        root.finishStructure()
        encoder.writeCompleted()

        then:
        output.toString(StandardCharsets.UTF_8) ==
"""[[products]]
name = 'Hammer'

[products.details]
weight = 500

[[products.variants]]
color = 'red'

[[products.variants]]
color = 'blue'
"""
    }

    void "manual encoder rejects duplicate keys"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new TableRootEncoder(output, LimitingStream.DEFAULT_LIMITS, false)

        when:
        def root = encoder.encodeObject(Argument.of(Map))
        root.encodeKey('name')
        root.encodeString('first')
        root.encodeKey('name')
        root.encodeString('second')

        then:
        thrown(Exception)
    }

    void "manual encoder rejects non-object root"() {
        given:
        def output = new ByteArrayOutputStream()
        def encoder = new TableRootEncoder(output, LimitingStream.DEFAULT_LIMITS, false)

        when:
        encoder.encodeString('bare string')
        encoder.writeCompleted()

        then:
        thrown(Exception)
    }
}
