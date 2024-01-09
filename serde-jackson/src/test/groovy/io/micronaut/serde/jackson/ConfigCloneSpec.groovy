package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.config.DeserializationConfiguration
import io.micronaut.serde.config.SerdeConfiguration
import io.micronaut.serde.config.SerializationConfiguration
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.Specification

class ConfigCloneSpec extends Specification {
    def 'clone'() {
        given:
        def ctx = ApplicationContext.run()
        def original = ctx.getBean(ObjectMapper)
        def modified = original.cloneWithConfiguration(ctx.getBean(OciSerdeConfiguration), ctx.getBean(OciSerializationConfiguration), ctx.getBean(OciDeserializationConfiguration))
        def testBean = new TestBean(empty: [], bytes: new byte[] {1, 2})

        expect:
        original.writeValueAsString(testBean) == '{"bytes":[1,2]}'
        modified.writeValueAsString(testBean) == '{"empty":[],"bytes":"AQI="}'
        original.writeValueAsString(testBean) == '{"bytes":[1,2]}' // double-check the old one wasn't broken

        original.writeValueAsString(testBean) == '{"bytes":[1,2]}'
        modified.writeValueAsString(testBean) == '{"empty":[],"bytes":"AQI="}'
        original.writeValueAsString(testBean) == '{"bytes":[1,2]}' // double-check the old one wasn't broken

        when:
        original.readValue('{"missing":"foo"}', TestBean)
        then:
        noExceptionThrown()
        when:
        modified.readValue('{"missing":"foo"}', TestBean)
        then:
        thrown SerdeException
        when:
        original.readValue('{"missing":"foo"}', TestBean)
        then:
        noExceptionThrown()

        cleanup:
        ctx.close()
    }

    @Serdeable
    static class TestBean {
        List<String> empty
        byte[] bytes
    }

    @ConfigurationProperties("oci.serde")
    @Bean(typed = OciSerdeConfiguration.class)
    static interface OciSerdeConfiguration extends SerdeConfiguration {
        @Override
        @Bindable(defaultValue = "false")
        boolean isWriteBinaryAsArray();
    }

    @ConfigurationProperties("oci.serde.serialization")
    @Bean(typed = OciSerializationConfiguration.class)
    static interface OciSerializationConfiguration extends SerializationConfiguration {
        @Bindable(defaultValue = "NON_NULL")
        @Override
        SerdeConfig.SerInclude getInclusion();
    }

    // micronaut-oraclecloud doesn't do this but we need some deser test
    @ConfigurationProperties("oci.serde.deserialization")
    @Bean(typed = OciDeserializationConfiguration.class)
    static interface OciDeserializationConfiguration extends DeserializationConfiguration {
        @Bindable(defaultValue = "false")
        @Override
        boolean isIgnoreUnknown()
    }
}
