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
package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeableGenerated;

import java.util.List;
import java.util.Map;

/**
 * Mirrors the Java shape Kotlin emits for {@code data class Node(val name: String, val children: Map<String, List<Node>>)}:
 * the type refers back to itself only through the bound of a wildcard.
 */
@SerdeableGenerated
@Introspected
public final class SourceGenWildcardRecursiveBean {
    private final String name;
    private final Map<String, ? extends List<SourceGenWildcardRecursiveBean>> children;

    public SourceGenWildcardRecursiveBean(String name, Map<String, ? extends List<SourceGenWildcardRecursiveBean>> children) {
        this.name = name;
        this.children = children;
    }

    public String getName() {
        return name;
    }

    public Map<String, ? extends List<SourceGenWildcardRecursiveBean>> getChildren() {
        return children;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SourceGenWildcardRecursiveBean that
            && java.util.Objects.equals(name, that.name)
            && java.util.Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, children);
    }
}
