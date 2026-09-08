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
package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll

/**
 * Generated serializers apply {@code micronaut.serde.serialization.inclusion} themselves instead of
 * falling back to the runtime object serializer, so every inclusion has to produce exactly what the
 * runtime path produces for the same model and payload.
 */
class GeneratedInclusionParitySpec extends JsonCompileSpec {

    private static final List<String> INCLUSIONS = [
        'ALWAYS', 'USE_DEFAULTS', 'NON_NULL', 'NON_ABSENT', 'NON_EMPTY', 'NON_DEFAULT', 'NEVER'
    ]

    @Unroll
    void 'test generated bean serializer matches the runtime serializer for inclusion #inclusion'() {
        given:
        def generatedContext = ApplicationContext.run(['micronaut.serde.serialization.inclusion': inclusion])
        def runtimeContext = ApplicationContext.run([
            'micronaut.serde.serialization.inclusion'                   : inclusion,
            'micronaut.serde.serialization.disable-generated-serializer': true
        ])
        def generatedMapper = generatedContext.getBean(JsonMapper)
        def runtimeMapper = runtimeContext.getBean(JsonMapper)
        Argument argument = Argument.of(SourceGenInclusionShapeBean)

        expect:
        assertGeneratedSerializer(generatedContext.getBean(SerdeRegistry), argument, true)
        assertGeneratedSerializer(runtimeContext.getBean(SerdeRegistry), argument, false)

        and:
        beanPayloads().every { payload ->
            String generatedJson = serializeToString(generatedMapper, payload)
            String runtimeJson = serializeToString(runtimeMapper, payload)
            assert generatedJson == runtimeJson
            true
        }

        cleanup:
        generatedContext.close()
        runtimeContext.close()

        where:
        inclusion << INCLUSIONS
    }

    @Unroll
    void 'test generated record serializer matches the runtime serializer for inclusion #inclusion'() {
        given:
        def generatedContext = ApplicationContext.run(['micronaut.serde.serialization.inclusion': inclusion])
        def runtimeContext = ApplicationContext.run([
            'micronaut.serde.serialization.inclusion'                   : inclusion,
            'micronaut.serde.serialization.disable-generated-serializer': true
        ])
        def generatedMapper = generatedContext.getBean(JsonMapper)
        def runtimeMapper = runtimeContext.getBean(JsonMapper)
        Argument argument = Argument.of(SourceGenInclusionShapeRecord)

        expect:
        assertGeneratedSerializer(generatedContext.getBean(SerdeRegistry), argument, true)
        assertGeneratedSerializer(runtimeContext.getBean(SerdeRegistry), argument, false)

        and:
        recordPayloads().every { payload ->
            String generatedJson = serializeToString(generatedMapper, payload)
            String runtimeJson = serializeToString(runtimeMapper, payload)
            assert generatedJson == runtimeJson
            true
        }

        cleanup:
        generatedContext.close()
        runtimeContext.close()

        where:
        inclusion << INCLUSIONS
    }

    void 'test generated serializers resolve the inclusion once per serializer instance'() {
        given:
        String source = generatedTestSource('io.micronaut.serde.jackson.compiletime.SerdeSourceGenInclusionShapeBeanSerializer')

        expect: 'the configuration lookup happens in the constructor, not per property'
        source.contains('this.include = GeneratedSerdeInclusionUtil.resolveInclusion(context);')
        source.contains('this.includeAll = GeneratedSerdeInclusionUtil.includeAlways(this.include);')
        source.count('resolveInclusion') == 1
        source.count('this.includeAll ||') == source.count('keysAwareEncoder.encodeKey(')

        and: 'each property kind uses the check matching the serde that writes it'
        source.contains('GeneratedSerdeInclusionUtil.shouldSerializeString(this.include, ')
        source.contains('GeneratedSerdeInclusionUtil.shouldSerializeBoolean(this.include, ')
        source.contains('GeneratedSerdeInclusionUtil.shouldSerializeCharacter(this.include, ')
        source.contains('GeneratedSerdeInclusionUtil.shouldSerializeNumber(this.include, ')
        source.contains('GeneratedSerdeInclusionUtil.shouldSerializePrimitive(this.include, ')
        source.contains('GeneratedSerdeInclusionUtil.shouldSerialize(this.include, context, ')
    }

    private static List<SourceGenInclusionShapeBean> beanPayloads() {
        return [
            newBean {},
            newBean {
                it.text = 'value'
                it.boxedFlag = Boolean.TRUE
                it.boxedLetter = Character.valueOf('a' as char)
                it.boxedByte = (byte) 1
                it.boxedShort = (short) 1
                it.boxedInt = 1
                it.boxedLong = 1L
                it.boxedFloat = 1.5F
                it.boxedDouble = 1.5D
                it.bigInteger = BigInteger.ONE
                it.bigDecimal = BigDecimal.ONE
                it.flag = true
                it.letter = 'a' as char
                it.count = 1
                it.id = 1L
                it.ratio = 1.5F
                it.score = 1.5D
                it.tags = ['a']
                it.attributes = [a: 'b']
            },
            newBean {
                it.text = ''
                it.boxedFlag = Boolean.FALSE
                it.boxedLetter = Character.valueOf((char) 0)
                it.boxedByte = (byte) 0
                it.boxedShort = (short) 0
                it.boxedInt = 0
                it.boxedLong = 0L
                it.boxedFloat = 0F
                it.boxedDouble = 0D
                it.bigInteger = BigInteger.ZERO
                it.bigDecimal = BigDecimal.ZERO
                it.tags = []
                it.attributes = [:]
            },
            newBean {
                it.boxedFloat = -0.0F
                it.boxedDouble = -0.0D
                it.ratio = -0.0F
                it.score = -0.0D
            },
            newBean {
                it.boxedFloat = Float.NaN
                it.boxedDouble = Double.NaN
                it.ratio = Float.NaN
                it.score = Double.NaN
            }
        ]
    }

    private static List<SourceGenInclusionShapeRecord> recordPayloads() {
        return beanPayloads().collect { bean ->
            new SourceGenInclusionShapeRecord(
                bean.text,
                bean.boxedFlag,
                bean.boxedLetter,
                bean.boxedByte,
                bean.boxedShort,
                bean.boxedInt,
                bean.boxedLong,
                bean.boxedFloat,
                bean.boxedDouble,
                bean.bigInteger,
                bean.bigDecimal,
                bean.flag,
                bean.letter,
                bean.count,
                bean.id,
                bean.ratio,
                bean.score,
                bean.tags,
                bean.attributes
            )
        }
    }

    private static SourceGenInclusionShapeBean newBean(Closure<?> configurer) {
        def bean = new SourceGenInclusionShapeBean()
        configurer.call(bean)
        return bean
    }

    private static void assertGeneratedSerializer(SerdeRegistry registry, Argument argument, boolean generated) {
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        assert (serializer.class.name == generatedClassName(argument.type, 'Serializer')) == generated
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        String packageName = type.package.name
        String localName = type.name.substring(packageName.length() + 1)
        "${packageName}.Serde${localName.replace('.', '_').replace('$', '_')}${suffix}"
    }
}
