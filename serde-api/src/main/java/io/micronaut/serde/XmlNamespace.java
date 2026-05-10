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
package io.micronaut.serde;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;

/**
 * Internal contract for XML encoders that can attach a namespace URI to the
 * next root element about to be written.
 *
 * @since 3.0.0
 */
@Internal
public interface XmlNamespace {

    /**
     * Set the namespace URI for the upcoming root element.
     *
     * @param namespaceUri the namespace URI
     */
    void setPendingRootNamespace(@NonNull String namespaceUri);
}
