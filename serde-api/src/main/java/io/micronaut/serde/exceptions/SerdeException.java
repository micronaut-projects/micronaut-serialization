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
package io.micronaut.serde.exceptions;

import io.micronaut.serde.exceptions.path.ReferencePath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Parent exception type for all serialization / deserialization exceptions.
 */
public class SerdeException extends IOException {

    private final List<ReferencePath> path = new ArrayList<>(10);

    public SerdeException() {
    }

    public SerdeException(String message) {
        super(message);
    }

    public SerdeException(String message, Throwable cause) {
        super(message, cause);
    }

    public SerdeException(Throwable cause) {
        super(cause);
    }

    /**
     * The mutable collection of the referenced path.
     *
     * @return The refenced path of the exception
     * @see 2.14
     */
    public List<ReferencePath> getPath() {
        return path;
    }

    /**
     * @return The referenced path string representation
     * @see 2.14
     */
    public String getPathAsString() {
        StringBuilder builder = new StringBuilder();
        ListIterator<ReferencePath> iterator = path.listIterator(path.size());
        while (iterator.hasPrevious()) {
            builder.append(iterator.previous().toString());
            if (iterator.hasPrevious()) {
                builder.append("->");
            }
        }
        return builder.toString();
    }
}
