/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.serde.jackson

import com.fasterxml.jackson.annotation.JsonView
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.json.JsonMapper
import org.intellij.lang.annotations.Language

abstract class JsonViewArgumentSpec extends JsonCompileSpec {

    // for some reason this method isn't resolvable when placed on the superclass?
    ApplicationContext buildJsonContext(String className, @Language("java") String cls, Map properties) {
        def context = super.buildContext(className, cls, true, properties)
        Thread.currentThread().setContextClassLoader(context.classLoader)
        jsonMapper = context.getBean(JsonMapper)
        return context
    }

    def createSpecialized() {
        given:
        def ctx = buildJsonContext('example.WithViews', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonView(Public.class)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class WithViews {
    public String firstName;
    public String lastName;
    @JsonView(Internal.class)
    public String birthdate;
}

class Public {}

class Internal extends Public {}
''', ['jackson.json-view.enabled': 'true', 'mock.introspections.enabled': 'true'])

        def withViews = newInstance(ctx, 'example.WithViews')
        withViews.firstName = 'Bob'
        withViews.lastName = 'Jones'
        withViews.birthdate = '08/01/1980'

        def publicMetadata = new MutableAnnotationMetadata()
        publicMetadata.addAnnotation(JsonView.class.name, ['value':[ctx.classLoader.loadClass('example.Public')]])
        def internalMetadata = new MutableAnnotationMetadata()
        internalMetadata.addAnnotation(JsonView.class.name, ['value':[ctx.classLoader.loadClass('example.Internal')]])

        expect:
        jsonMapper.createSpecific(Argument.ofInstance(withViews)).writeValueAsString(withViews) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980"}'
        jsonMapper.createSpecific(Argument.ofInstance(withViews).withAnnotationMetadata(publicMetadata)).writeValueAsString(Argument.ofInstance(withViews).withAnnotationMetadata(publicMetadata), withViews) ==
                '{"firstName":"Bob","lastName":"Jones"}'
        jsonMapper.createSpecific(Argument.ofInstance(withViews).withAnnotationMetadata(internalMetadata)).writeValueAsString(Argument.ofInstance(withViews).withAnnotationMetadata(internalMetadata), withViews) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980"}'

        cleanup:
        ctx.close()
    }

    def writeTyped() {
        given:
        def ctx = buildJsonContext('example.WithViews', '''
package example;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonView(Public.class)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class WithViews {
    public String firstName;
    public String lastName;
    @JsonView(Internal.class)
    public String birthdate;
}

class Public {}

class Internal extends Public {}
''', ['jackson.json-view.enabled': 'true', 'mock.introspections.enabled': 'true'])

        def withViews = newInstance(ctx, 'example.WithViews')
        withViews.firstName = 'Bob'
        withViews.lastName = 'Jones'
        withViews.birthdate = '08/01/1980'

        def publicMetadata = new MutableAnnotationMetadata()
        publicMetadata.addAnnotation(JsonView.class.name, ['value':[ctx.classLoader.loadClass('example.Public')]])
        def internalMetadata = new MutableAnnotationMetadata()
        internalMetadata.addAnnotation(JsonView.class.name, ['value':[ctx.classLoader.loadClass('example.Internal')]])

        expect:
        jsonMapper.writeValueAsString(Argument.ofInstance(withViews), withViews) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980"}'
        jsonMapper.writeValueAsString(Argument.ofInstance(withViews).withAnnotationMetadata(publicMetadata), withViews) ==
                '{"firstName":"Bob","lastName":"Jones"}'
        jsonMapper.writeValueAsString(Argument.ofInstance(withViews).withAnnotationMetadata(internalMetadata), withViews) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980"}'

        cleanup:
        ctx.close()
    }
}
