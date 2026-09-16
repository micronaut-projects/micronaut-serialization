package io.micronaut.serde

import io.micronaut.serde.config.CoercionPolicy
import spock.lang.Specification

class DelegatingDecoderCoercionSpec extends Specification {

    void "the policy of the delegate is used"() {
        given:
        def delegate = Stub(Decoder) {
            getCoercionPolicy() >> CoercionPolicy.STRICT
        }

        expect:
        new TestDelegatingDecoder(delegate: delegate).getCoercionPolicy().is(CoercionPolicy.STRICT)
    }

    void "a delegate that is not available yet falls back to the default"() {
        expect: 'the caller hits the same failure when it decodes, so this does not rethrow'
        new TestDelegatingDecoder().getCoercionPolicy().is(CoercionPolicy.LENIENT)
    }

    static class TestDelegatingDecoder extends DelegatingDecoder {
        Decoder delegate

        @Override
        protected Decoder delegate() throws IOException {
            if (delegate == null) {
                throw new IOException('not ready')
            }
            return delegate
        }

        @Override
        IOException createDeserializationException(String message, Object invalidValue) {
            return new IOException(message)
        }
    }
}
