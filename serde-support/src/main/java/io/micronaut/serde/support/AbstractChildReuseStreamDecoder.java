/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.serde.support;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;

/**
 * {@link AbstractStreamDecoder} extension that does not need to allocate for child decoders.
 */
@Internal
public abstract class AbstractChildReuseStreamDecoder extends AbstractStreamDecoder {
    private int depth = 0;

    protected AbstractChildReuseStreamDecoder(@NonNull AbstractStreamDecoder parent) {
        super(parent);
    }

    protected AbstractChildReuseStreamDecoder(@NonNull Class<?> view) {
        super(view);
    }

    @Override
    AbstractStreamDecoder childDecoder() {
        depth++;
        parent = this;
        return this;
    }

    @Override
    void checkChild() {
    }

    @Override
    void transferControlToParent() throws IOException {
        if (--depth == 0) {
            parent = null;
        }
        this.backFromChild(this);
    }
}
