package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.BootstrapContextCompatible
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.BootstrapPropertySourceLocator
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.type.Argument
import io.micronaut.core.util.StringUtils
import io.micronaut.json.JsonMapper
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class BootstrapContextSpec extends Specification {

    @RestoreSystemProperties
    void 'mapper should be available in bootstrap context'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, StringUtils.TRUE)
        def ctx = ApplicationContext.run(['spec.name': 'BootstrapContextSpec'])

        expect:
        ctx.getRequiredProperty('bootstrap-deser', MyBean).foo == 'bar'

        cleanup:
        ctx.close()
    }

    @Requires(property = 'spec.name', value = 'BootstrapContextSpec')
    @Singleton
    @BootstrapContextCompatible
    static class Tester implements BootstrapPropertySourceLocator {
        private final JsonMapper mapper

        Tester(JsonMapper mapper) {
            this.mapper = mapper
        }

        @Override
        Iterable<PropertySource> findPropertySources(Environment environment) throws ConfigurationException {
            return [PropertySource.of(['bootstrap-deser': mapper.readValue('{"foo":"bar"}', Argument.of(MyBean))])]
        }
    }

    @Serdeable
    static class MyBean {
        String foo
    }
}
