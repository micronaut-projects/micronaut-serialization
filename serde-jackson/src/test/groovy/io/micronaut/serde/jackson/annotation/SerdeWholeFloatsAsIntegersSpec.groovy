package io.micronaut.serde.jackson.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.config.SerializationConfiguration
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll

/**
 * {@code SerializationConfiguration.Feature.WRITE_WHOLE_FLOATS_AS_INTEGERS}.
 *
 * <p>No annotation names the feature: {@code JsonFormat.Feature} of Jackson has no constant for it. The tests set it
 * on a bean and on a property with {@code io.micronaut.serde.jackson.features.Features}, which is mapped to the
 * members of {@code SerdeConfig} that {@code @JsonFormat(with = …, without = …)} is mapped to.</p>
 */
class SerdeWholeFloatsAsIntegersSpec extends JsonCompileSpec {

    private static final Map<String, Object> ON = ['micronaut.serde.serialization.write-whole-floats-as-integers': true]

    private static final String WITH_FEATURE = '@Features(with = "WRITE_WHOLE_FLOATS_AS_INTEGERS")'
    private static final String WITHOUT_FEATURE = '@Features(without = "WRITE_WHOLE_FLOATS_AS_INTEGERS")'

    private static final String IMPORTS = '''
import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.jackson.features.Features;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
'''

    private static final String WHOLE = '{"primitive":900,"boxed":900,"single":900,"boxedSingle":900,' +
        '"array":[900,0.5],"singles":[900,0.5],"list":[900,0.5],"map":{"a":900,"b":0.5},"optional":900,' +
        '"maybe":900,"number":900,"any":900,"anyList":[900,"a"],"anyMap":{"a":900}}'

    private static final String FRACTIONS = '{"primitive":900.0,"boxed":900.0,"single":900.0,"boxedSingle":900.0,' +
        '"array":[900.0,0.5],"singles":[900.0,0.5],"list":[900.0,0.5],"map":{"a":900.0,"b":0.5},"optional":900.0,' +
        '"maybe":900.0,"number":900.0,"any":900.0,"anyList":[900.0,"a"],"anyMap":{"a":900.0}}'

    private static String numbersSource(String typeAnnotation) {
        """
package test;
$IMPORTS
@Serdeable
$typeAnnotation
record Numbers(double primitive, Double boxed, float single, Float boxedSingle,
               double[] array, float[] singles, List<Double> list, Map<String, Double> map,
               OptionalDouble optional, Optional<Double> maybe, Number number,
               Object any, List<Object> anyList, Map<String, Object> anyMap) {
}
"""
    }

    private Object numbers(ApplicationContext context) {
        newInstance(context, 'test.Numbers', [
            900d, 900d, 900f, 900f,
            [900d, 0.5d] as double[], [900f, 0.5f] as float[], [900d, 0.5d], [a: 900d, b: 0.5d],
            OptionalDouble.of(900d), Optional.of(900d), 900d,
            900d, [900d, 'a'], [a: 900d]
        ] as Object[])
    }

    void "a whole floating-point value is written with a fraction when the feature is off"() {
        given:
        def context = buildContext('test.Numbers', numbersSource(''), true, [:])
        def configuration = context.getBean(SerializationConfiguration)

        expect:
        !configuration.writeWholeFloatsAsIntegers()
        !configuration.features().contains(SerializationConfiguration.Feature.WRITE_WHOLE_FLOATS_AS_INTEGERS)
        !SerializationConfiguration.DEFAULT_FEATURES.contains(SerializationConfiguration.Feature.WRITE_WHOLE_FLOATS_AS_INTEGERS)
        writeJson(jsonMapper, numbers(context)) == FRACTIONS

        cleanup:
        context.close()
    }

    void "a whole floating-point value is written as an integer when the feature is on by configuration"() {
        given:
        def context = buildContext('test.Numbers', numbersSource(''), true, ON)
        def configuration = context.getBean(SerializationConfiguration)

        expect:
        configuration.writeWholeFloatsAsIntegers()
        configuration.features().contains(SerializationConfiguration.Feature.WRITE_WHOLE_FLOATS_AS_INTEGERS)
        writeJson(jsonMapper, numbers(context)) == WHOLE

        cleanup:
        context.close()
    }

    void "a whole floating-point value is written as an integer when the feature is on for the bean"() {
        given:
        def context = buildContext('test.Numbers', numbersSource(WITH_FEATURE), true, [:])

        expect:
        writeJson(jsonMapper, numbers(context)) == WHOLE

        cleanup:
        context.close()
    }

    void "the feature of a property applies to that property alone"() {
        given:
        def context = buildContext('test.Test', """
package test;
$IMPORTS
@Serdeable
record Test($WITH_FEATURE double with, double without,
            $WITH_FEATURE Double boxedWith, Double boxedWithout,
            $WITH_FEATURE float singleWith, float singleWithout,
            $WITH_FEATURE double[] arrayWith, double[] arrayWithout,
            $WITH_FEATURE float[] singlesWith, float[] singlesWithout,
            $WITH_FEATURE List<Double> listWith, List<Double> listWithout,
            $WITH_FEATURE Map<String, Double> mapWith, Map<String, Double> mapWithout,
            $WITH_FEATURE OptionalDouble optionalWith, OptionalDouble optionalWithout,
            $WITH_FEATURE Optional<Double> maybeWith, Optional<Double> maybeWithout,
            $WITH_FEATURE Number numberWith, Number numberWithout,
            $WITH_FEATURE Object anyWith, Object anyWithout,
            $WITH_FEATURE List<Object> anyListWith, List<Object> anyListWithout) {
}
""", true, [:])
        def bean = newInstance(context, 'test.Test', [
            900d, 900d, 900d, 900d, 900f, 900f,
            [900d] as double[], [900d] as double[], [900f] as float[], [900f] as float[],
            [900d], [900d], [a: 900d], [a: 900d],
            OptionalDouble.of(900d), OptionalDouble.of(900d), Optional.of(900d), Optional.of(900d),
            900d, 900d, 900d, 900d, [900d], [900d]
        ] as Object[])

        expect:
        writeJson(jsonMapper, bean) == '{"with":900,"without":900.0,"boxedWith":900,"boxedWithout":900.0,' +
            '"singleWith":900,"singleWithout":900.0,"arrayWith":[900],"arrayWithout":[900.0],' +
            '"singlesWith":[900],"singlesWithout":[900.0],"listWith":[900],"listWithout":[900.0],' +
            '"mapWith":{"a":900},"mapWithout":{"a":900.0},"optionalWith":900,"optionalWithout":900.0,' +
            '"maybeWith":900,"maybeWithout":900.0,"numberWith":900,"numberWithout":900.0,' +
            '"anyWith":900,"anyWithout":900.0,"anyListWith":[900],"anyListWithout":[900.0]}'

        cleanup:
        context.close()
    }

    @Unroll
    void "a property switches the feature off for itself when the feature is on #where"() {
        given:
        def context = buildContext('test.Test', """
package test;
$IMPORTS
@Serdeable
$typeAnnotation
record Test(double on, $WITHOUT_FEATURE double off,
            List<Double> listOn, $WITHOUT_FEATURE List<Double> listOff,
            double[] arrayOn, $WITHOUT_FEATURE double[] arrayOff,
            OptionalDouble optionalOn, $WITHOUT_FEATURE OptionalDouble optionalOff,
            Number numberOn, $WITHOUT_FEATURE Number numberOff,
            Object anyOn, $WITHOUT_FEATURE Object anyOff) {
}
""", true, configuration)
        def bean = newInstance(context, 'test.Test', [
            900d, 900d, [900d], [900d], [900d] as double[], [900d] as double[],
            OptionalDouble.of(900d), OptionalDouble.of(900d), 900d, 900d, 900d, 900d
        ] as Object[])

        expect:
        writeJson(jsonMapper, bean) == '{"on":900,"off":900.0,"listOn":[900],"listOff":[900.0],' +
            '"arrayOn":[900],"arrayOff":[900.0],"optionalOn":900,"optionalOff":900.0,' +
            '"numberOn":900,"numberOff":900.0,"anyOn":900,"anyOff":900.0}'

        cleanup:
        context.close()

        where:
        where              | typeAnnotation | configuration
        'for the bean'     | WITH_FEATURE   | [:]
        'by configuration' | ''             | ON
    }

    @Unroll
    void "the double #value is written as #expected"() {
        given:
        def context = buildContext('test.Test', """
package test;
$IMPORTS
@Serdeable
record Test(double primitive, Double boxed, double[] array, OptionalDouble optional, Number number, Object any) {
}
""", true, ON)
        double d = value
        def bean = newInstance(context, 'test.Test', [d, d, [d] as double[], OptionalDouble.of(d), d, d] as Object[])

        expect:
        writeJson(jsonMapper, bean) == '{"primitive":' + expected + ',"boxed":' + expected + ',"array":[' + expected +
            '],"optional":' + expected + ',"number":' + expected + ',"any":' + expected + '}'

        cleanup:
        context.close()

        where:
        value                    | expected
        900d                     | '900'
        -900d                    | '-900'
        0d                       | '0'
        -0d                      | '0'
        0.5d                     | '0.5'
        -1.5d                    | '-1.5'
        900.0000001d             | '900.0000001'
        9007199254740992d        | '9007199254740992'      // 2^53
        -9007199254740992d       | '-9007199254740992'
        9007199254740994d        | '9.007199254740994E15'  // the next double after 2^53
        -9007199254740994d       | '-9.007199254740994E15'
        1.0E20d                  | '1.0E20'
        Double.MAX_VALUE         | '1.7976931348623157E308'
        Double.MIN_VALUE         | '4.9E-324'
        Double.NaN               | '"NaN"'
        Double.POSITIVE_INFINITY | '"Infinity"'
        Double.NEGATIVE_INFINITY | '"-Infinity"'
    }

    @Unroll
    void "the float #value is written as #expected"() {
        given:
        def context = buildContext('test.Test', """
package test;
$IMPORTS
@Serdeable
record Test(float primitive, Float boxed, float[] array, Number number, Object any) {
}
""", true, ON)
        float f = value
        def bean = newInstance(context, 'test.Test', [f, f, [f] as float[], f, f] as Object[])

        expect:
        writeJson(jsonMapper, bean) == '{"primitive":' + expected + ',"boxed":' + expected + ',"array":[' + expected +
            '],"number":' + expected + ',"any":' + expected + '}'

        cleanup:
        context.close()

        where:
        value                   | expected
        900f                    | '900'
        -0f                     | '0'
        0.5f                    | '0.5'
        16777216f               | '16777216'              // 2^24, the last whole number before a float skips one
        9007199254740992f       | '9007199254740992'      // 2^53
        18014398509481984f      | '1.8014399E16'          // 2^54
        1.0E20f                 | '1.0E20'
        Float.NaN               | '"NaN"'
        Float.POSITIVE_INFINITY | '"Infinity"'
    }

    void "a pattern or a string shape wins over the feature"() {
        given:
        def context = buildContext('test.Test', """
package test;
$IMPORTS
@Serdeable
record Test(@JsonFormat(pattern = "0.00") double pattern,
            @JsonFormat(shape = JsonFormat.Shape.STRING) double string,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Float boxedString,
            @JsonFormat(shape = JsonFormat.Shape.NUMBER) double number,
            @JsonFormat(shape = JsonFormat.Shape.NUMBER) Float boxedNumber,
            BigDecimal decimal, int integer, long big) {
}
""", true, ON)
        def bean = newInstance(context, 'test.Test', [900d, 900d, 900f, 900d, 900f, new BigDecimal('900.0'), 900, 900L] as Object[])

        expect:
        writeJson(jsonMapper, bean) == '{"pattern":"900.00","string":"900.0","boxedString":"900.0",' +
            '"number":900,"boxedNumber":900,"decimal":900.0,"integer":900,"big":900}'

        cleanup:
        context.close()
    }

    void "what the feature writes is read back"() {
        given:
        def context = buildContext('test.Numbers', numbersSource(''), true, ON)
        def written = numbers(context)

        when:
        String json = writeJson(jsonMapper, written)
        def read = jsonMapper.readValue(json, argumentOf(context, 'test.Numbers'))

        then:
        json == WHOLE
        read.primitive == 900d
        read.boxed == 900d
        read.single == 900f
        read.boxedSingle == 900f
        read.array == [900d, 0.5d] as double[]
        read.singles == [900f, 0.5f] as float[]
        read.list == [900d, 0.5d]
        read.list[0] instanceof Double
        read.map == [a: 900d, b: 0.5d]
        read.map.a instanceof Double
        read.optional == OptionalDouble.of(900d)
        read.maybe == Optional.of(900d)
        read.number == 900

        cleanup:
        context.close()
    }

    void "a value that is not the property of a bean is written with the feature of the configuration"() {
        given:
        def context = buildContext('test.Numbers', numbersSource(''), true, ON)

        expect:
        writeJson(jsonMapper, 900d) == '900'
        writeJson(jsonMapper, 900f) == '900'
        writeJson(jsonMapper, [900d, 0.5d]) == '[900,0.5]'
        writeJson(jsonMapper, [a: 900d]) == '{"a":900}'
        writeJson(jsonMapper, [900d] as double[]) == '[900]'
        writeJson(jsonMapper, [900f] as float[]) == '[900]'
        writeJson(jsonMapper, OptionalDouble.of(900d)) == '900'

        cleanup:
        context.close()
    }

    void "a floating-point key of a map is written as the value is"() {
        given:
        def context = buildContext('test.Test', """
package test;
$IMPORTS
@Serdeable
record Test(Map<Double, String> keys, $WITHOUT_FEATURE Map<Double, String> fractions) {
}
""", true, ON)
        def bean = newInstance(context, 'test.Test', [[(900d): 'a', (0.5d): 'b'], [(900d): 'a']] as Object[])

        when:
        String json = writeJson(jsonMapper, bean)

        then:
        json == '{"keys":{"900":"a","0.5":"b"},"fractions":{"900.0":"a"}}'
        jsonMapper.readValue(json, argumentOf(context, 'test.Test')) == bean

        cleanup:
        context.close()
    }

    void "a generated serializer steps aside for the feature of the configuration"() {
        given:
        String source = """
package test;
$IMPORTS
@Serdeable
record Test(String name, double value, Double boxed, float single) {
}
"""

        when: 'the feature is off'
        def context = buildContext('test.Test', source, true, [:])

        then: 'the generated serializer writes the bean'
        specificSerializer(context, 'test.Test').class.name == 'test.SerdeTestSerializer'
        writeJson(jsonMapper, newInstance(context, 'test.Test', ['a', 900d, 900d, 900f] as Object[])) ==
            '{"name":"a","value":900.0,"boxed":900.0,"single":900.0}'

        when: 'the feature is on'
        context.close()
        context = buildContext('test.Test', source, true, ON)

        then: 'the generated serializer is there, and the run-time serializer writes the bean'
        context.classLoader.loadClass('test.SerdeTestSerializer')
        specificSerializer(context, 'test.Test').class.name != 'test.SerdeTestSerializer'
        writeJson(jsonMapper, newInstance(context, 'test.Test', ['a', 900d, 900d, 900f] as Object[])) ==
            '{"name":"a","value":900,"boxed":900,"single":900}'

        cleanup:
        context.close()
    }

    @Unroll
    void "a bean that sets the feature #where has no generated serializer"() {
        given:
        def context = buildContext('test.Test', """
package test;
$IMPORTS
@Serdeable
$typeAnnotation
record Test(String name, $propertyAnnotation double value) {
}
""", true, [:])

        when:
        context.classLoader.loadClass('test.SerdeTestSerializer')

        then:
        thrown(ClassNotFoundException)
        writeJson(jsonMapper, newInstance(context, 'test.Test', ['a', 900d] as Object[])) == '{"name":"a","value":900}'

        cleanup:
        context.close()

        where:
        where            | typeAnnotation | propertyAnnotation
        'for itself'     | WITH_FEATURE   | ''
        'for a property' | ''             | WITH_FEATURE
    }

    void "the feature of a bean reaches the beans it holds"() {
        given:
        def context = buildContext('test.Outer', """
package test;
$IMPORTS
@Serdeable
$WITH_FEATURE
record Outer(double value, Inner inner, List<Inner> inners, Map<String, Inner> byName, Object any) {
}

@Serdeable
record Inner(double value, List<Double> values) {
}
""", true, [:])
        def inner = newInstance(context, 'test.Inner', [900d, [900d]] as Object[])
        def outer = newInstance(context, 'test.Outer', [900d, inner, [inner], [a: inner], inner] as Object[])

        expect:
        writeJson(jsonMapper, outer) == '{"value":900,"inner":{"value":900,"values":[900]},' +
            '"inners":[{"value":900,"values":[900]}],"byName":{"a":{"value":900,"values":[900]}},' +
            '"any":{"value":900,"values":[900]}}'

        and: 'the bean is written without the feature where it is not held by a bean that has it'
        writeJson(jsonMapper, inner) == '{"value":900.0,"values":[900.0]}'

        cleanup:
        context.close()
    }

    void "a bean written without the feature first is written with it where a bean that has the feature holds it"() {
        given:
        def context = buildContext('test.Outer', """
package test;
$IMPORTS
@Serdeable
$WITH_FEATURE
record Outer(double value, Inner inner) {
}

@Serdeable
record Inner(double value) {
}
""", true, [:])
        def inner = newInstance(context, 'test.Inner', [900d] as Object[])
        def outer = newInstance(context, 'test.Outer', [900d, inner] as Object[])

        expect:
        writeJson(jsonMapper, inner) == '{"value":900.0}'
        writeJson(jsonMapper, outer) == '{"value":900,"inner":{"value":900}}'
        writeJson(jsonMapper, inner) == '{"value":900.0}'

        cleanup:
        context.close()
    }

    private static Object specificSerializer(ApplicationContext context, String className) {
        def type = Argument.of(context.classLoader.loadClass(className))
        def registry = context.getBean(SerdeRegistry)
        def specific = registry.findSerializer(type).createSpecific(registry.newEncoderContext(Object), type)
        specific.respondsTo('getSerializer') ? specific.getSerializer() : specific
    }
}
