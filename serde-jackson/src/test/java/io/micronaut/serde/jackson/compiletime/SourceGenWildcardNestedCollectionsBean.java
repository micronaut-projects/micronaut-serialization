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
 * Mirrors the Java shape Kotlin emits for a data class with nested collection properties such as
 * {@code Map<String, Map<String, String>>}: declaration-site variance turns the nested value type
 * into an upper-bounded wildcard, {@code Map<String, ? extends Map<String, String>>}.
 */
@SerdeableGenerated
@Introspected
public final class SourceGenWildcardNestedCollectionsBean {
    private final Map<String, ? extends Map<String, String>> deepMap;
    private final Map<String, ? extends Map<String, Item>> deepObjectMap;
    private final List<? extends List<String>> deepList;

    public SourceGenWildcardNestedCollectionsBean(Map<String, ? extends Map<String, String>> deepMap,
                                                  Map<String, ? extends Map<String, Item>> deepObjectMap,
                                                  List<? extends List<String>> deepList) {
        this.deepMap = deepMap;
        this.deepObjectMap = deepObjectMap;
        this.deepList = deepList;
    }

    public Map<String, ? extends Map<String, String>> getDeepMap() {
        return deepMap;
    }

    public Map<String, ? extends Map<String, Item>> getDeepObjectMap() {
        return deepObjectMap;
    }

    public List<? extends List<String>> getDeepList() {
        return deepList;
    }

    /**
     * A nested model value.
     *
     * @param name The name
     */
    @SerdeableGenerated
    @Introspected
    public record Item(String name) {
    }
}
