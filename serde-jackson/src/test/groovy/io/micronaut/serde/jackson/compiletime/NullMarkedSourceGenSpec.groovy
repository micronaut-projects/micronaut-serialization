package io.micronaut.serde.jackson.compiletime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.nullmarked.NullMarkedBean
import io.micronaut.serde.jackson.nullmarked.NullMarkedCollectionRecord
import io.micronaut.serde.jackson.nullmarked.NullMarkedConstructorBean
import io.micronaut.serde.jackson.nullmarked.NullMarkedPlainRecord
import io.micronaut.serde.jackson.nullmarked.NullMarkedRenamedRecord
import io.micronaut.serde.jackson.nullmarked.NullMarkedStatus
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The fixtures live in the {@code nullMarkedSourceGen} source set, which is compiled with NullAway
 * enabled, so the generated serdes for them have to be NullAway-compatible for this spec to run.
 */
class NullMarkedSourceGenSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    JsonMapper jsonMapper = context.getBean(JsonMapper)

    @Shared
    SerdeRegistry registry = context.getBean(SerdeRegistry)

    void 'test null-marked #type.simpleName uses generated serdes'() {
        given:
        Argument argument = Argument.of(type)

        expect:
        registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument).class.name == generatedClassName(type, 'Serializer')
        registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument).class.name == generatedClassName(type, 'Deserializer')

        where:
        type << [
            NullMarkedPlainRecord,
            NullMarkedRenamedRecord,
            NullMarkedCollectionRecord,
            NullMarkedBean,
            NullMarkedConstructorBean
        ]
    }

    void 'test null-marked records round trip through generated serdes'() {
        given:
        def renamed = new NullMarkedRenamedRecord('Ada', NullMarkedStatus.ACTIVE, new NullMarkedPlainRecord('nested', 2))
        def collections = new NullMarkedCollectionRecord(['a', 'b'], [x: 1], [new NullMarkedPlainRecord('item', 3)], null)

        when:
        String renamedJson = jsonMapper.writeValueAsString(renamed)
        String collectionsJson = jsonMapper.writeValueAsString(collections)

        then:
        renamedJson == '{"display_name":"Ada","state":"ACTIVE","nested":{"name":"nested","count":2}}'
        jsonMapper.readValue(renamedJson, NullMarkedRenamedRecord) == renamed
        collectionsJson == '{"tags":["a","b"],"counts":{"x":1},"items":[{"name":"item","count":3}]}'
        jsonMapper.readValue(collectionsJson, NullMarkedCollectionRecord) == collections
    }

    void 'test null-marked beans round trip through generated serdes'() {
        given:
        def bean = new NullMarkedBean()
        bean.name = 'Ada'
        bean.tags = ['a']
        bean.plain = new NullMarkedPlainRecord('nested', 2)

        when:
        String beanJson = jsonMapper.writeValueAsString(bean)
        NullMarkedBean decodedBean = jsonMapper.readValue(beanJson, NullMarkedBean)
        NullMarkedConstructorBean decodedConstructorBean = jsonMapper.readValue('{"name":"Ada","tags":["a"]}', NullMarkedConstructorBean)

        then:
        beanJson == '{"bean_name":"Ada","tags":["a"],"plain":{"name":"nested","count":2}}'
        decodedBean.name == 'Ada'
        decodedBean.tags == ['a']
        decodedBean.plain == bean.plain
        decodedConstructorBean.name == 'Ada'
        decodedConstructorBean.tags == ['a']
    }

    void 'test absent non-null component keeps the runtime default'() {
        // Matches the runtime object deserializer: an absent component is only rejected when
        // strict-nullable or require-all-creator-parameters is enabled
        expect:
        jsonMapper.readValue('{"count":1}', NullMarkedPlainRecord) == new NullMarkedPlainRecord(null, 1)
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        "${type.package.name}.Serde${type.simpleName}${suffix}"
    }
}
