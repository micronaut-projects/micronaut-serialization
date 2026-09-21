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
package io.micronaut.serde.support.patch;

import io.micronaut.serde.patch.JsonPatchException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guards patch shape and operation count while serde reads the document. Duplicate names must be
 * recorded before JsonNode deserialization collapses them, but whether they are relevant depends
 * on the operation's name, which can appear after its other members.
 */
final class PatchDocumentReader implements TokenReader {
    private final TokenReader delegate;
    private final int maxOperations;
    private final List<Set<String>> scopes = new ArrayList<>();
    private final List<Members> operations = new ArrayList<>();
    private String field = "";

    PatchDocumentReader(TokenReader delegate, int maxOperations) throws IOException {
        TokenIO.require(delegate, PatchToken.START_ARRAY);
        this.delegate = delegate;
        this.maxOperations = maxOperations;
        scopes.add(Set.of());
    }

    boolean hasDuplicates(int index, boolean sourceRequired, boolean valueRequired) {
        Members members = operations.get(index);
        return members.duplicates.contains("op") || members.duplicates.contains("path")
            || (sourceRequired && members.duplicates.contains("from"))
            || (valueRequired && (members.duplicates.contains("value") || members.duplicateValue));
    }

    @Override
    public @Nullable PatchToken current() {
        return delegate.current();
    }

    @Override
    public String text() {
        return delegate.text();
    }

    @Override
    public void next() throws IOException {
        delegate.next();
        PatchToken token = current();
        if (token == null) {
            return;
        }
        if (scopes.size() == 1 && token != PatchToken.END_ARRAY) {
            int index = operations.size();
            if (index >= maxOperations || token != PatchToken.START_OBJECT) {
                throw new JsonPatchException(index, "", "", index >= maxOperations
                    ? "Operation count limit exceeded" : "Expected an operation object");
            }
            operations.add(new Members());
            field = "";
        }
        switch (token) {
            case START_OBJECT -> scopes.add(new HashSet<>());
            case START_ARRAY -> scopes.add(Set.of());
            case END_OBJECT, END_ARRAY -> scopes.removeLast();
            case KEY -> {
                String key = text();
                boolean duplicate = !scopes.getLast().add(key);
                if (scopes.size() == 2) {
                    field = key;
                    if (duplicate) {
                        operations.getLast().duplicates.add(key);
                    }
                } else if (duplicate && field.equals("value")) {
                    operations.getLast().duplicateValue = true;
                }
            }
            default -> { }
        }
    }

    private static final class Members {
        private final Set<String> duplicates = new HashSet<>();
        private boolean duplicateValue;
    }
}
