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

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import spock.lang.Specification

class RuntimeResolverSerdeIntrospectionsSpec extends Specification {

    void "caches resolver introspections by argument and kind"() {
        given:
        BeanIntrospection introspection = Stub(BeanIntrospection)
        int serializationResolutions = 0
        int deserializationResolutions = 0
        SerdeIntrospections resolverBacked = throwingIntrospections().withRuntimeIntrospectionResolver(new SerdeIntrospections.RuntimeIntrospectionResolver() {
            @Override
            Object cacheKey() {
                "test"
            }

            @Override
            def <T> Optional<BeanIntrospection<T>> resolve(SerdeIntrospections.RuntimeIntrospectionRequest<T> request) {
                if (request.kind() == SerdeIntrospections.RuntimeIntrospectionKind.SERIALIZATION) {
                    serializationResolutions++
                } else {
                    deserializationResolutions++
                }
                Optional.of((BeanIntrospection<T>) introspection)
            }
        })
        Argument<RuntimeBean> argument = Argument.of(RuntimeBean)

        expect:
        resolverBacked.getSerializableIntrospection(argument).is(introspection)
        resolverBacked.getSerializableIntrospection(argument).is(introspection)
        serializationResolutions == 1

        resolverBacked.getDeserializableIntrospection(argument).is(introspection)
        resolverBacked.getDeserializableIntrospection(argument).is(introspection)
        deserializationResolutions == 1
    }

    private static SerdeIntrospections throwingIntrospections() {
        new SerdeIntrospections() {
            @Override
            def <T> BeanIntrospection<T> getSerializableIntrospection(Argument<T> type) {
                throw new AssertionError("Delegate should not be used")
            }

            @Override
            def <T> BeanIntrospection<T> getDeserializableIntrospection(Argument<T> type) {
                throw new AssertionError("Delegate should not be used")
            }
        }
    }

    static final class RuntimeBean {
    }
}
