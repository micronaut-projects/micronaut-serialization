/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.processor.jackson;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

/**
 * Abstract transformer that validate supported members returned by {@link #getSupportedMemberNames()}.
 * @param <A> The annotation type.
 */
public abstract class ValidatingAnnotationTransformer<A extends Annotation> implements TypedAnnotationTransformer<A> {

    @Override
    public final List<AnnotationValue<?>> transform(AnnotationValue<A> annotation, VisitorContext visitorContext) {
        Set<String> supported = getSupportedMemberNames();
        final Set<CharSequence> memberNames = annotation.getMemberNames();
        final Optional<CharSequence> unsupportedMember = memberNames.stream()
                .filter(n -> !supported.contains(n.toString()))
                .findFirst();
        return unsupportedMember.<List<AnnotationValue<?>>>map(charSequence -> Collections.singletonList(
                AnnotationValue.builder(SerdeConfig.SerdeError.class)
                        .value(getErrorMessage(supported, charSequence))
                        .build()
        )).orElseGet(() -> transformValid(annotation, visitorContext));
    }

    private String getErrorMessage(Set<String> supported, CharSequence member) {
        return "Annotation @" + annotationType().getSimpleName() + " specifies attribute '" + member + "'"
                       + ". Currently supported attributes include: " + supported;
    }

    /**
     * The transform method will be called for each instances of the annotation returned via this method.
     *
     * @param annotation The annotation values
     * @param visitorContext The context that is being visited
     * @return A list of zero or many annotations and values to map to
     */
    protected abstract List<AnnotationValue<?>> transformValid(AnnotationValue<A> annotation, VisitorContext visitorContext);

    /**
     * @return The set of annotation member names that are supported.
     */
    protected @NonNull Set<String> getSupportedMemberNames() {
        return Collections.emptySet();
    }
}
