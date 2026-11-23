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
package io.micronaut.serde.support.util;

import io.micronaut.core.annotation.Internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The beans of one kind being initialized by the current thread, innermost last. A bean that refers back to a bean
 * still being initialized, through a cycle of properties, holds the serdes of that bean, so it is bound to the
 * document whenever that bean turns out to be, which is only known once that bean completes.
 *
 * @param <B> The bean type
 * @since 3.3.0
 */
@Internal
public final class BeanInitializations<B> {

    private final ThreadLocal<ArrayDeque<B>> initializing = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Records that the bean starts initializing.
     *
     * @param bean The bean
     */
    public void enter(B bean) {
        initializing.get().addLast(bean);
    }

    /**
     * Records that the innermost bean completed its initialization.
     */
    public void exit() {
        initializing.get().removeLast();
    }

    /**
     * @param bean A bean still being initialized, reached again through a cycle
     * @return The beans that started initializing after it, which depend on its serdes
     */
    public List<B> initializingWithin(B bean) {
        List<B> within = new ArrayList<>();
        for (Iterator<B> iterator = initializing.get().descendingIterator(); iterator.hasNext(); ) {
            B next = iterator.next();
            if (next == bean) {
                return within;
            }
            within.add(next);
        }
        return List.of();
    }
}
