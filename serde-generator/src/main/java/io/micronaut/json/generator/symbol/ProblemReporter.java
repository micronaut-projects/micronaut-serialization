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
package io.micronaut.json.generator.symbol;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.List;

@Internal
public final class ProblemReporter {
    private final List<Problem> problems = new ArrayList<>();
    private boolean failed = false;

    public void fail(String message, @Nullable Element element) {
        problems.add(new Problem(Level.FAIL, message, element));
        failed = true;
    }

    public void warn(String message, @Nullable Element element) {
        problems.add(new Problem(Level.WARN, message, element));
    }

    public void info(String message, @Nullable Element element) {
        problems.add(new Problem(Level.INFO, message, element));
    }

    public void reportTo(VisitorContext context) {
        for (Problem problem : problems) {
            switch (problem.level) {
                case INFO:
                    context.info(problem.message, problem.element);
                    break;
                case WARN:
                    context.warn(problem.message, problem.element);
                    break;
                case FAIL:
                    context.fail(problem.message, problem.element);
                    break;
            }
        }
    }

    public void throwOnFailures() {
        if (isFailed()) {
            StringBuilder msg = new StringBuilder("Generation failure: ");
            for (Problem problem : problems) {
                msg.append('\n').append(problem.level).append(' ').append(problem.message);
            }
            throw new AssertionError(msg);
        }
    }

    public boolean isFailed() {
        return failed;
    }

    private static final class Problem {
        final Level level;
        final String message;
        final Element element;

        Problem(Level level, String message, Element element) {
            this.message = message;
            this.element = element;
            this.level = level;
        }
    }

    private enum Level {
        INFO, WARN, FAIL
    }
}
