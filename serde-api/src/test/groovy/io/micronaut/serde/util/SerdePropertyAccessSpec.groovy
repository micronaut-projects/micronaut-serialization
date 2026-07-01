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
package io.micronaut.serde.util

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanProperty
import io.micronaut.core.beans.BeanReadProperty
import io.micronaut.core.beans.BeanWriteProperty
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.serde.config.annotation.SerdeConfig
import spock.lang.Specification

class SerdePropertyAccessSpec extends Specification {
    private static final String JACKSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty"
    private static final String ACCESS = "access"
    private static final String ACCESS_KIND = "accessKind"

    void "default metadata allows serialization and deserialization"() {
        expect:
        SerdePropertyAccess.canSerialize(AnnotationMetadata.EMPTY_METADATA)
        SerdePropertyAccess.canDeserialize(AnnotationMetadata.EMPTY_METADATA)
        !SerdePropertyAccess.hasRestrictedAccess(AnnotationMetadata.EMPTY_METADATA)
    }

    void "bean property read and write flags restrict access"() {
        given:
        BeanProperty<?, ?> writeOnly = Stub() {
            isWriteOnly() >> true
        }
        BeanProperty<?, ?> readOnly = Stub() {
            isReadOnly() >> true
        }
        BeanReadProperty<?, ?> readProperty = Stub()
        BeanWriteProperty<?, ?> writeProperty = Stub()

        expect:
        !SerdePropertyAccess.canSerialize(writeOnly, AnnotationMetadata.EMPTY_METADATA)
        SerdePropertyAccess.canSerialize(readProperty, AnnotationMetadata.EMPTY_METADATA)
        !SerdePropertyAccess.canDeserialize(readOnly, AnnotationMetadata.EMPTY_METADATA)
        SerdePropertyAccess.canDeserialize(writeProperty, AnnotationMetadata.EMPTY_METADATA)
    }

    void "serde access metadata restricts access"() {
        expect:
        !SerdePropertyAccess.canSerialize(serdeMetadata(SerdeConfig.WRITE_ONLY))
        SerdePropertyAccess.canDeserialize(serdeMetadata(SerdeConfig.WRITE_ONLY))
        SerdePropertyAccess.hasRestrictedAccess(serdeMetadata(SerdeConfig.WRITE_ONLY))

        SerdePropertyAccess.canSerialize(serdeMetadata(SerdeConfig.READ_ONLY))
        !SerdePropertyAccess.canDeserialize(serdeMetadata(SerdeConfig.READ_ONLY))
        SerdePropertyAccess.hasRestrictedAccess(serdeMetadata(SerdeConfig.READ_ONLY))
    }

    void "jackson property access metadata restricts access"() {
        given:
        def readOnly = annotationMetadata(JACKSON_PROPERTY, [(ACCESS): Access.READ_ONLY])
        def writeOnly = annotationMetadata(JACKSON_PROPERTY, [(ACCESS): "com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY"])

        expect:
        SerdePropertyAccess.canSerialize(readOnly)
        !SerdePropertyAccess.canDeserialize(readOnly)
        SerdePropertyAccess.hasRestrictedAccess(readOnly)

        !SerdePropertyAccess.canSerialize(writeOnly)
        SerdePropertyAccess.canDeserialize(writeOnly)
        SerdePropertyAccess.hasRestrictedAccess(writeOnly)
    }

    void "introspected property access metadata restricts access"() {
        given:
        def readOnly = introspectedAccessMetadata([Introspected.Property.Access.READ])
        def writeOnly = introspectedAccessMetadata(Introspected.Property.Access.WRITE)
        def readWrite = introspectedAccessMetadata([
            Introspected.Property.Access.READ,
            Introspected.Property.Access.WRITE
        ] as Introspected.Property.Access[])

        expect:
        SerdePropertyAccess.canSerialize(readOnly)
        !SerdePropertyAccess.canDeserialize(readOnly)
        SerdePropertyAccess.hasRestrictedAccess(readOnly)

        !SerdePropertyAccess.canSerialize(writeOnly)
        SerdePropertyAccess.canDeserialize(writeOnly)
        SerdePropertyAccess.hasRestrictedAccess(writeOnly)

        SerdePropertyAccess.canSerialize(readWrite)
        SerdePropertyAccess.canDeserialize(readWrite)
        !SerdePropertyAccess.hasRestrictedAccess(readWrite)
    }

    private static MutableAnnotationMetadata serdeMetadata(String access) {
        annotationMetadata(SerdeConfig.name, [(access): true])
    }

    private static MutableAnnotationMetadata introspectedAccessMetadata(Object accessKind) {
        annotationMetadata(Introspected.Property.name, [(ACCESS_KIND): accessKind])
    }

    private static MutableAnnotationMetadata annotationMetadata(String annotationName, Map<CharSequence, Object> values) {
        def metadata = new MutableAnnotationMetadata()
        metadata.addAnnotation(annotationName, values)
        metadata
    }

    private enum Access {
        READ_ONLY,
        WRITE_ONLY
    }
}
