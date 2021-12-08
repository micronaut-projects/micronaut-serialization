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
package io.micronaut.json.generator;

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.json.generator.symbol.ProblemReporter;
import io.micronaut.json.generator.symbol.SingletonSerializerGenerator;

import javax.annotation.processing.Filer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract class AbstractGeneratorVisitor<ANN> implements TypeElementVisitor<ANN, ANN> {
    private final List<SingletonSerializerGenerator.GenerationResult> generated = new ArrayList<>();

    @Override
    public abstract void visitClass(ClassElement element, VisitorContext context);

    protected final void generateFromSymbol(VisitorContext context, Function<ProblemReporter, Collection<SingletonSerializerGenerator.GenerationResult>> generate) {
        ProblemReporter problemReporter = new ProblemReporter();
        Collection<SingletonSerializerGenerator.GenerationResult> generationResult = generate.apply(problemReporter);

        problemReporter.reportTo(context);
        if (!problemReporter.isFailed()) {
            generated.addAll(generationResult);
        }
    }

    @Override
    public void finish(VisitorContext context) {
        if (!generated.isEmpty()) {
            if (context instanceof JavaVisitorContext) {
                Filer filer = ((JavaVisitorContext) context).getProcessingEnv().getFiler();
                try {
                    for (SingletonSerializerGenerator.GenerationResult generationResult : generated) {
                        generationResult.getGeneratedFile().writeTo(filer);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                boolean groovyContext;
                try {
                    groovyContext = context instanceof GroovyVisitorContext;
                } catch (NoClassDefFoundError e) {
                    groovyContext = false;
                }
                if (groovyContext) {
                    try {
                        GroovyAuxCompiler.compile(
                                (GroovyVisitorContext) context,
                                generated.stream().map(SingletonSerializerGenerator.GenerationResult::getGeneratedFile).collect(Collectors.toList())
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
            generated.clear();
        }
    }

    @Override
    @NonNull
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
