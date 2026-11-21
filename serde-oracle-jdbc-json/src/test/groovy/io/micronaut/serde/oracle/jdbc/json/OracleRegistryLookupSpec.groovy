package io.micronaut.serde.oracle.jdbc.json

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.Encoder
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.config.SerdeConfiguration
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

/**
 * The serdes of the Oracle mappers are looked up in their registry, which can override the serdes of the
 * context, and only the serdes that the context would also find are cached.
 */
class OracleRegistryLookupSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void "a registry that overrides a serializer is used by every call, not only the first one"() {
        given:
        def registry = new OverridingRegistry(context.getBean(SerdeRegistry))
        def binary = new OracleJdbcJsonBinaryObjectMapper(registry, context.getBean(SerdeConfiguration))
        def text = new OracleJdbcJsonTextObjectMapper(registry, context.getBean(SerdeConfiguration))
        def book = new OsonBook(id: 1L, title: "a", pages: 1)

        expect:
        3.times {
            assert text.writeValueAsString(book) == '"overridden"'
            assert binary.readValue(binary.writeValueAsBytes(Argument.of(OsonBook), book), String) == 'overridden'
        }
        binary.serdeCache.@serializers.isEmpty()
        text.serdeCache.@serializers.isEmpty()
    }

    void "the serializer of a type the registry does not override is looked up once"() {
        given:
        def registry = new OverridingRegistry(context.getBean(SerdeRegistry))
        def text = new OracleJdbcJsonTextObjectMapper(registry, context.getBean(SerdeConfiguration))

        when:
        def written = (1..3).collect { text.writeValueAsString(Argument.of(Pages), new Pages(pages: it)) }

        then:
        written == ['{"pages":1}', '{"pages":2}', '{"pages":3}']
        registry.lookups.get() == 1
        text.serdeCache.size() == 1
    }

    static class OverridingRegistry implements SerdeRegistry {
        @Delegate
        final SerdeRegistry delegate
        final AtomicInteger lookups = new AtomicInteger()

        OverridingRegistry(SerdeRegistry delegate) {
            this.delegate = delegate
        }

        @Override
        <T> Serializer<? super T> findSerializer(Argument<? extends T> forType) throws SerdeException {
            if (forType.type == OsonBook) {
                return new Serializer<Object>() {
                    @Override
                    void serialize(Encoder encoder, Serializer.EncoderContext ctx, Argument<?> type, Object value) {
                        encoder.encodeString("overridden")
                    }
                }
            }
            lookups.incrementAndGet()
            return delegate.findSerializer(forType)
        }
    }
}
