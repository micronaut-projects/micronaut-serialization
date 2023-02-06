package io.micronaut.serde.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.BootstrapContextCompatible
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.BootstrapPropertySourceLocator
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.type.Argument
import io.micronaut.core.util.StringUtils
import io.micronaut.discovery.config.ConfigurationClient
import io.micronaut.json.JsonMapper
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class BootstrapContextSpec extends Specification {

    @RestoreSystemProperties
    void 'mapper should be available in bootstrap context'() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, StringUtils.TRUE)
        def ctx = ApplicationContext.run([
                'spec.name': 'BootstrapContextSpec',
                (ConfigurationClient.ENABLED): true])

        expect:
        ctx.getRequiredProperty('bootstrap-deser', MyBean).foo == 'bar'

        cleanup:
        ctx.close()
    }

    @Requires(property = 'spec.name', value = 'BootstrapContextSpec')
    @Singleton
    @BootstrapContextCompatible
    static class Tester implements ConfigurationClient {
        private final ObjectMapper mapper

        Tester(ObjectMapper mapper) {
            this.mapper = mapper
        }

        @Override
        Publisher<PropertySource> getPropertySources(Environment environment) {
            return Publishers.just(PropertySource.of(['bootstrap-deser': mapper.readValue('{"foo":"bar", "strings":["one", "two"]}', Argument.of(MyBean))]))
        }

        @Override
        String getDescription() {
            return "test client"
        }
    }

    @Serdeable
    static class MyBean {
        String foo
        List<String> strings
    }
}
