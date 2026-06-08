package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.Decoder
import io.micronaut.serde.Encoder
import io.micronaut.serde.Keys
import io.micronaut.serde.KeysAwareDecoder
import io.micronaut.serde.KeysAwareEncoder
import io.micronaut.serde.KeysSupport
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.compiletime.SourceGenRuntimeConstructorDefaults
import io.micronaut.serde.jackson.compiletime.SourceGenRuntimeMixedProperties
import io.micronaut.serde.jackson.compiletime.SourceGenRuntimePropertyDefaults
import spock.lang.Specification
import tools.jackson.core.SerializableString
import tools.jackson.core.json.JsonFactory

import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

class KeysAwareDecoderSpec extends Specification {

    void "decodeKey matches known keys and preserves fallback for unknown keys"() {
        given:
            def parser = new JsonFactory().createParser('{"known":1,"unknown":2,"other":3}')
            def decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            def objectDecoder = (KeysAwareDecoder) decoder.decodeObject()
            def keys = Keys.create(["known", "other"])
            def jacksonKeysIndex = KeysSupport.indexOf(new JacksonKeysProvider())

        when:
            def firstKey = objectDecoder.decodeKey(keys)
            def firstValue = objectDecoder.decodeInt()
            def unknownKey = objectDecoder.decodeKey(keys)
            def unknownName = objectDecoder.decodeKey()
            objectDecoder.skipValue()
            def secondKey = objectDecoder.decodeKey(keys)
            def secondValue = objectDecoder.decodeInt()
            def endKey = objectDecoder.decodeKey(keys)
            def noKey = objectDecoder.decodeKey()
            objectDecoder.finishStructure()

        then:
            KeysSupport.get(keys, jacksonKeysIndex)[JacksonKeysProvider.PROPERTY_NAME_MATCHER_INDEX] != null
            firstKey == 0
            firstValue == 1
            unknownKey == KeysAwareDecoder.MATCH_UNKNOWN_NAME
            unknownName == "unknown"
            secondKey == 1
            secondValue == 3
            endKey == KeysAwareDecoder.MATCH_END_OBJECT
            noKey == null

        cleanup:
            parser?.close()
    }

    void "decodeKey falls back to matcher for known keys that are not sequential"() {
        given:
            def parser = new JsonFactory().createParser('{"other":3,"known":1}')
            def decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            def objectDecoder = (KeysAwareDecoder) decoder.decodeObject()
            def keys = Keys.create(["known", "other"])

        when:
            def firstKey = objectDecoder.decodeKey(keys)
            def firstValue = objectDecoder.decodeInt()
            def secondKey = objectDecoder.decodeKey(keys)
            def secondValue = objectDecoder.decodeInt()
            def endKey = objectDecoder.decodeKey(keys)
            objectDecoder.finishStructure()

        then:
            firstKey == 1
            firstValue == 3
            secondKey == 0
            secondValue == 1
            endKey == KeysAwareDecoder.MATCH_END_OBJECT

        cleanup:
            parser?.close()
    }

    void "decodeKey matches known keys case-insensitively"() {
        given:
            def parser = new JsonFactory().createParser('{"KNOWN":1,"unknown":2,"Other":3}')
            def decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            def objectDecoder = (KeysAwareDecoder) decoder.decodeObject()
            def keys = Keys.create(["known", "other"], true)

        when:
            def firstKey = objectDecoder.decodeKey(keys)
            def firstValue = objectDecoder.decodeInt()
            def unknownKey = objectDecoder.decodeKey(keys)
            def unknownName = objectDecoder.decodeKey()
            objectDecoder.skipValue()
            def secondKey = objectDecoder.decodeKey(keys)
            def secondValue = objectDecoder.decodeInt()
            def endKey = objectDecoder.decodeKey(keys)
            objectDecoder.finishStructure()

        then:
            keys.caseInsensitive()
            firstKey == 0
            firstValue == 1
            unknownKey == KeysAwareDecoder.MATCH_UNKNOWN_NAME
            unknownName == "unknown"
            secondKey == 1
            secondValue == 3
            endKey == KeysAwareDecoder.MATCH_END_OBJECT

        cleanup:
            parser?.close()
    }

    void "fallback keys-aware decoder uses key set map and preserves unknown key"() {
        given:
            def parser = new JsonFactory().createParser('{"known":1,"unknown":2,"other":3}')
            def decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            def plainKeyReads = new AtomicInteger()
            def objectDecoder = KeysAwareDecoder.of(plainDecoderProxy(decoder.decodeObject(), plainKeyReads))
            def keys = Keys.create(["known", "other"])

        when:
            def firstKey = objectDecoder.decodeKey(keys)
            def firstValue = objectDecoder.decodeInt()
            def unknownKey = objectDecoder.decodeKey(keys)
            def unknownName = objectDecoder.decodeKey()
            objectDecoder.skipValue()
            def secondKey = objectDecoder.decodeKey(keys)
            def secondValue = objectDecoder.decodeInt()
            def endKey = objectDecoder.decodeKey(keys)
            objectDecoder.finishStructure()

        then:
            firstKey == 0
            firstValue == 1
            unknownKey == KeysAwareDecoder.MATCH_UNKNOWN_NAME
            unknownName == "unknown"
            secondKey == 1
            secondValue == 3
            endKey == KeysAwareDecoder.MATCH_END_OBJECT
            plainKeyReads.get() == 4

        cleanup:
            parser?.close()
    }

    void "fallback keys-aware decoder uses case-insensitive key set map"() {
        given:
            def parser = new JsonFactory().createParser('{"KNOWN":1,"unknown":2,"Other":3}')
            def decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            def plainKeyReads = new AtomicInteger()
            def objectDecoder = KeysAwareDecoder.of(plainDecoderProxy(decoder.decodeObject(), plainKeyReads))
            def keys = Keys.create(["known", "other"], true)

        when:
            def firstKey = objectDecoder.decodeKey(keys)
            def firstValue = objectDecoder.decodeInt()
            def unknownKey = objectDecoder.decodeKey(keys)
            def unknownName = objectDecoder.decodeKey()
            objectDecoder.skipValue()
            def secondKey = objectDecoder.decodeKey(keys)
            def secondValue = objectDecoder.decodeInt()
            def endKey = objectDecoder.decodeKey(keys)
            objectDecoder.finishStructure()

        then:
            firstKey == 0
            firstValue == 1
            unknownKey == KeysAwareDecoder.MATCH_UNKNOWN_NAME
            unknownName == "unknown"
            secondKey == 1
            secondValue == 3
            endKey == KeysAwareDecoder.MATCH_END_OBJECT
            plainKeyReads.get() == 4

        cleanup:
            parser?.close()
    }

    void "runtime constructor deserializer uses keys-aware simple path"() {
        given:
            def context = ApplicationContext.run([
                'micronaut.serde.deserialization.disable-generated-deserializer': true
            ])
            def registry = context.getBean(SerdeRegistry)
            def argument = Argument.of(SourceGenRuntimeConstructorDefaults)
            def deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
            def parser = new JsonFactory().createParser('{"name":"Ada","active":true,"count":42,"nullableName":"Grace","nullableActive":false}')
            def decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            def keyMatches = new AtomicInteger()
            def countingDecoder = keysAwareDecoderProxy(decoder, keyMatches)

        when:
            def result = deserializer.deserialize(countingDecoder, registry.newDecoderContext(Object), argument)

        then:
            keyMatches.get() == 5
            result.name() == 'Ada'
            result.active()
            result.count() == 42
            result.nullableName() == 'Grace'
            !result.nullableActive()

        cleanup:
            parser?.close()
            context?.close()
    }

    void "runtime simple object deserializer uses keys-aware path"() {
        given:
            def context = ApplicationContext.run([
                'micronaut.serde.deserialization.disable-generated-deserializer': true
            ])
            def registry = context.getBean(SerdeRegistry)
            def argument = Argument.of(SourceGenRuntimePropertyDefaults)
            def deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
            def parser = new JsonFactory().createParser('{"name":"Ada","active":true,"count":42,"nullableName":"Grace","nullableActive":false}')
            def decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            def keyMatches = new AtomicInteger()
            def countingDecoder = keysAwareDecoderProxy(decoder, keyMatches)

        when:
            def result = deserializer.deserialize(countingDecoder, registry.newDecoderContext(Object), argument)

        then:
            keyMatches.get() == 6
            result.name == 'Ada'
            result.active
            result.count == 42
            result.nullableName == 'Grace'
            !result.nullableActive

        cleanup:
            parser?.close()
            context?.close()
    }

    void "runtime simple object deserializer uses keys-aware path for case-insensitive properties"() {
        given:
            def context = ApplicationContext.run([
                'micronaut.serde.deserialization.disable-generated-deserializer': true,
                'micronaut.serde.deserialization.accept-case-insensitive-properties': true
            ])
            def registry = context.getBean(SerdeRegistry)
            def argument = Argument.of(SourceGenRuntimePropertyDefaults)
            def deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
            def parser = new JsonFactory().createParser('{"NAME":"Ada","ACTIVE":true,"COUNT":42,"NULLABLENAME":"Grace","NULLABLEACTIVE":false}')
            def decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            def keyMatches = new AtomicInteger()
            def plainKeyReads = new AtomicInteger()
            def countingDecoder = keysAwareDecoderProxy(decoder, keyMatches, plainKeyReads)

        when:
            def result = deserializer.deserialize(countingDecoder, registry.newDecoderContext(Object), argument)

        then:
            keyMatches.get() == 6
            plainKeyReads.get() == 0
            result.name == 'Ada'
            result.active
            result.count == 42
            result.nullableName == 'Grace'
            !result.nullableActive

        cleanup:
            parser?.close()
            context?.close()
    }

    void "runtime specific object deserializer uses keys-aware path"() {
        given:
            def context = ApplicationContext.run([
                'micronaut.serde.deserialization.disable-generated-deserializer': true
            ])
            def registry = context.getBean(SerdeRegistry)
            def argument = Argument.of(SourceGenRuntimeMixedProperties)
            def deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
            def parser = new JsonFactory().createParser('{"name":"Ada","active":true,"count":42,"nullableName":"Grace"}')
            def decoder = JacksonDecoder.create(parser, LimitingStream.DEFAULT_LIMITS)
            def keyMatches = new AtomicInteger()
            def plainKeyReads = new AtomicInteger()
            def countingDecoder = keysAwareDecoderProxy(decoder, keyMatches, plainKeyReads)

        when:
            def result = deserializer.deserialize(countingDecoder, registry.newDecoderContext(Object), argument)

        then:
            keyMatches.get() == 4
            plainKeyReads.get() == 0
            result.name == 'Ada'
            result.active
            result.count == 42
            result.nullableName == 'Grace'

        cleanup:
            parser?.close()
            context?.close()
    }

    void "encodeKey writes known keys from serializable keys"() {
        given:
            def outputStream = new ByteArrayOutputStream()
            def generator = new JsonFactory().createGenerator(outputStream)
            def encoder = JacksonEncoder.create(generator, LimitingStream.DEFAULT_LIMITS)
            def objectEncoder = (KeysAwareEncoder) encoder.encodeObject(Argument.OBJECT_ARGUMENT)
            def keys = Keys.create(["known", "other"])
            def jacksonKeysIndex = KeysSupport.indexOf(new JacksonKeysProvider())

        when:
            objectEncoder.encodeKey(keys, 0)
            objectEncoder.encodeInt(1)
            objectEncoder.encodeKey(keys, 1)
            objectEncoder.encodeInt(2)
            objectEncoder.finishStructure()
            generator.close()

        then:
            def serializableKeys = KeysSupport.get(keys, jacksonKeysIndex)[JacksonKeysProvider.SERIALIZABLE_KEYS_INDEX]
            serializableKeys instanceof SerializableString[]
            serializableKeys[0] instanceof SerializableString
            outputStream.toString("UTF-8") == '{"known":1,"other":2}'
    }

    void "fallback keys-aware encoder writes key names by index"() {
        given:
            def outputStream = new ByteArrayOutputStream()
            def generator = new JsonFactory().createGenerator(outputStream)
            def encoder = JacksonEncoder.create(generator, LimitingStream.DEFAULT_LIMITS)
            def plainKeyWrites = new AtomicInteger()
            def objectEncoder = KeysAwareEncoder.of(plainEncoderProxy(encoder.encodeObject(Argument.OBJECT_ARGUMENT), plainKeyWrites))
            def keys = Keys.create(["known", "other"])

        when:
            objectEncoder.encodeKey(keys, 0)
            objectEncoder.encodeInt(1)
            objectEncoder.encodeKey(keys, 1)
            objectEncoder.encodeInt(2)
            objectEncoder.finishStructure()
            generator.close()

        then:
            plainKeyWrites.get() == 2
            outputStream.toString("UTF-8") == '{"known":1,"other":2}'
    }

    void "runtime simple object serializer uses keys-aware encoder"() {
        given:
            def context = ApplicationContext.run([
                'micronaut.serde.serialization.disable-generated-serializer': true,
                'micronaut.serde.serialization.inclusion': 'ALWAYS'
            ])
            def registry = context.getBean(SerdeRegistry)
            def argument = Argument.of(SourceGenRuntimeConstructorDefaults)
            def serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
            def outputStream = new ByteArrayOutputStream()
            def generator = new JsonFactory().createGenerator(outputStream)
            def encoder = JacksonEncoder.create(generator, LimitingStream.DEFAULT_LIMITS)
            def keyWrites = new AtomicInteger()
            def countingEncoder = keysAwareEncoderProxy(encoder, keyWrites)
            def value = new SourceGenRuntimeConstructorDefaults('Ada', true, 42, 'Grace', false)

        when:
            serializer.serialize(countingEncoder, registry.newEncoderContext(Object), argument, value)
            generator.close()

        then:
            keyWrites.get() == 5
            outputStream.toString("UTF-8") == '{"name":"Ada","active":true,"count":42,"nullableName":"Grace","nullableActive":false}'

        cleanup:
            generator?.close()
            context?.close()
    }

    private static KeysAwareDecoder keysAwareDecoderProxy(Decoder delegate, AtomicInteger keyMatches) {
        return keysAwareDecoderProxy(delegate, keyMatches, new AtomicInteger())
    }

    private static KeysAwareDecoder keysAwareDecoderProxy(Decoder delegate, AtomicInteger keyMatches, AtomicInteger plainKeyReads) {
        return (KeysAwareDecoder) Proxy.newProxyInstance(
            KeysAwareDecoderSpec.classLoader,
            [KeysAwareDecoder] as Class[],
            { Object proxy, java.lang.reflect.Method method, Object[] args ->
                if (method.name == 'decodeKey' && args != null && args.length == 1 && args[0] instanceof Keys) {
                    keyMatches.incrementAndGet()
                } else if (method.name == 'decodeKey' && (args == null || args.length == 0)) {
                    plainKeyReads.incrementAndGet()
                }
                def result = method.invoke(delegate, args)
                if (result instanceof Decoder) {
                    return keysAwareDecoderProxy((Decoder) result, keyMatches, plainKeyReads)
                }
                return result
            } as InvocationHandler
        )
    }

    private static Decoder plainDecoderProxy(Decoder delegate, AtomicInteger plainKeyReads) {
        return (Decoder) Proxy.newProxyInstance(
            KeysAwareDecoderSpec.classLoader,
            [Decoder] as Class[],
            { Object proxy, java.lang.reflect.Method method, Object[] args ->
                if (method.name == 'decodeKey' && (args == null || args.length == 0)) {
                    plainKeyReads.incrementAndGet()
                }
                def result = method.invoke(delegate, args)
                if (result instanceof Decoder) {
                    return plainDecoderProxy((Decoder) result, plainKeyReads)
                }
                return result
            } as InvocationHandler
        )
    }

    private static KeysAwareEncoder keysAwareEncoderProxy(Encoder delegate, AtomicInteger keyWrites) {
        return (KeysAwareEncoder) Proxy.newProxyInstance(
            KeysAwareDecoderSpec.classLoader,
            [KeysAwareEncoder] as Class[],
            { Object proxy, java.lang.reflect.Method method, Object[] args ->
                if (method.name == 'encodeKey' && args != null && args.length == 2 && args[0] instanceof Keys) {
                    keyWrites.incrementAndGet()
                }
                def result = method.invoke(delegate, args)
                if (result instanceof Encoder) {
                    return keysAwareEncoderProxy((Encoder) result, keyWrites)
                }
                return result
            } as InvocationHandler
        )
    }

    private static Encoder plainEncoderProxy(Encoder delegate, AtomicInteger plainKeyWrites) {
        return (Encoder) Proxy.newProxyInstance(
            KeysAwareDecoderSpec.classLoader,
            [Encoder] as Class[],
            { Object proxy, java.lang.reflect.Method method, Object[] args ->
                if (method.name == 'encodeKey' && args != null && args.length == 1) {
                    plainKeyWrites.incrementAndGet()
                }
                def result = method.invoke(delegate, args)
                if (result instanceof Encoder) {
                    return plainEncoderProxy((Encoder) result, plainKeyWrites)
                }
                return result
            } as InvocationHandler
        )
    }
}
