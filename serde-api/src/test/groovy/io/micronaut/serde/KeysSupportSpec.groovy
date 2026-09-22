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
package io.micronaut.serde

import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.lang.ref.WeakReference

class KeysSupportSpec extends Specification {
    def 'provider registered after keys are created contributes keys'() {
        given:
        def keys = Keys.create('foo', 'bar')
        def provider = new LateKeysProvider()

        when:
        def contribution = KeysSupport.get(keys, KeysSupport.indexOf(provider))

        then:
        contribution[0] == ['foo', 'bar'] as String[]
    }

    def 'late provider is registered once and invoked lazily'() {
        given:
        def provider = new CountingKeysProvider()
        int index = KeysSupport.indexOf(provider)

        when:
        def keys = KeysSupport.createWithMetadata([new KeyDescriptor('Foo')], true)

        then:
        KeysSupport.indexOf(new CountingKeysProvider()) == index
        provider.invocations == 0

        when:
        def first = KeysSupport.get(keys, index)
        def second = KeysSupport.get(keys, index)

        then:
        first.is(second)
        first[0] == ['Foo'] as String[]
        first[1] == true
        provider.invocations == 1
    }

    def 'late provider does not retain its class loader'() {
        given:
        WeakReference<ClassLoader> loader = DisposableKeysProviderLoader.registerProvider(register)

        expect:
        new PollingConditions(timeout: 10).eventually {
            System.gc()
            assert loader.get() == null
        }

        where:
        register << [false, true]
    }

    private static final class LateKeysProvider implements KeysProvider {
        @Override
        Class<?> keysType() {
            LateKeysProvider
        }

        @Override
        Object[] create(List<String> keys) {
            [keys as String[]] as Object[]
        }
    }

    private static final class CountingKeysProvider implements KeysProvider {
        int invocations

        @Override
        Class<?> keysType() {
            CountingKeysProvider
        }

        @Override
        Object[] create(List<String> keys) {
            invocations++
            [keys as String[]] as Object[]
        }

        @Override
        Object[] createWithMetadata(List<KeyDescriptor> keys, boolean caseInsensitive) {
            invocations++
            [keys*.name() as String[], caseInsensitive] as Object[]
        }
    }
}
