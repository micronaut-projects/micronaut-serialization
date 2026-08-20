package io.micronaut.serde.support.deserializers

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.Deserializer
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.support.MyConstructorPropertiesBean
import io.micronaut.serde.support.MyMixSetterConstructorPropertiesBean
import io.micronaut.serde.support.MyRecord
import io.micronaut.serde.support.MySetterPropertiesBean
import io.micronaut.serde.support.NullableConstructorParent
import io.micronaut.serde.support.TestStatus
import io.micronaut.serde.support.util.JsonNodeDecoder
import spock.lang.Specification

class DeserializeSpec extends Specification {

    def 'deserialize'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(JsonMapper)

        when:"Can deserialize with null parameters for @Nullable field of java.lang.Object type"
        def result = mapper.readValue('{"valid":"false","message":"Invalid Input"}', Argument.of(TestStatus))
        then:
        !result.valid
        result.message == 'Invalid Input'
        result.additionalData == null

        when:"Deserialize with all parameters provided and non null"
        result = mapper.readValue('{"valid":"true","message":"In Progress","additionalData":["Step1 Passed", "Step2 InProgress"]}', Argument.of(TestStatus))
        then:
        result.valid
        result.message == 'In Progress'
        result.additionalData != null
        result.additionalData == ["Step1 Passed", "Step2 InProgress"]

        cleanup:
        ctx.close()
    }

    def 'test types'() {
        given:
            def ctx = ApplicationContext.run()

        when:
            def serdeRegistry = ctx.getBean(SerdeRegistry)
            def myRecordDeserializer = getDeserializer(serdeRegistry, MyRecord)

        then:
            isLegacyOrGenerated(myRecordDeserializer, SimpleRecordLikeObjectDeserializer)
            isLegacyOrGenerated(getDeserializer(serdeRegistry, MyConstructorPropertiesBean), SimpleRecordLikeObjectDeserializer)
            isLegacyOrGenerated(getDeserializer(serdeRegistry, MySetterPropertiesBean), SimpleObjectDeserializer)
            isLegacyOrGenerated(getDeserializer(serdeRegistry, MyMixSetterConstructorPropertiesBean), SpecificObjectDeserializer)
        cleanup:
            ctx.close()
    }

    def 'deserialize a nullable constructor-only property with all null values'() {
        given:
        def ctx = ApplicationContext.run()
        def serdeRegistry = ctx.getBean(SerdeRegistry)
        def objectDeserializer = ctx.getBean(ObjectDeserializer)
        def decoderContext = serdeRegistry.newDecoderContext(null)
        def parentBean = objectDeserializer.getDeserializableBean(Argument.of(NullableConstructorParent), null, decoderContext)
        def valueArgument = parentBean.injectProperties.derProperties[0].argument
        def valueBean = objectDeserializer.getDeserializableBean(valueArgument, null, decoderContext)
        def deserializer = new SpecificObjectDeserializer(false, valueBean, null)

        when:
        deserializer.deserializeNullable(
                JsonNodeDecoder.create(JsonNode.createObjectNode([:]), LimitingStream.DEFAULT_LIMITS),
                decoderContext,
                valueArgument)

        then:
        valueArgument.nullable
        def error = thrown(SerdeException)
        error.message == 'Null value encountered during deserialization of type: NullableConstructorValue value'

        cleanup:
        ctx.close()
    }

    private static Deserializer getDeserializer(SerdeRegistry serdeRegistry, Class clazz) {
        def deserializer = serdeRegistry.findDeserializer(Argument.of(clazz))
                .createSpecific(serdeRegistry.newDecoderContext(null), Argument.of(clazz))
        if (deserializer instanceof ErrorCatchingDeserializer) {
            return deserializer.deserializer
        }
        return deserializer
    }

    private static boolean isLegacyOrGenerated(Deserializer deserializer, Class legacyType) {
        deserializer.class.name.contains('.$Serde') || deserializer.class.name.contains('.Serde') || legacyType.isInstance(deserializer)
    }
}
