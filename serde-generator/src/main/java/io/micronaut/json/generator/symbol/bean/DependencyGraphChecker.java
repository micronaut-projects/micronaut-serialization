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
package io.micronaut.json.generator.symbol.bean;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.json.annotation.SerializableBean;
import io.micronaut.json.generator.symbol.GeneratorType;
import io.micronaut.json.generator.symbol.SerializerLinker;
import io.micronaut.json.generator.symbol.SerializerSymbol;

import java.util.Optional;

@Internal
public class DependencyGraphChecker {
    private final VisitorContext warningContext;
    private final SerializerLinker linker;

    private boolean anyFailures = false;

    public DependencyGraphChecker(VisitorContext warningContext, SerializerLinker linker) {
        this.warningContext = warningContext;
        this.linker = linker;
    }

    public void checkCircularDependencies(SerializerSymbol symbol, GeneratorType type, Element rootElement) {
        symbol.visitDependencies(new Node(null, type, rootElement), type);
    }

    public boolean hasAnyFailures() {
        return anyFailures;
    }

    private class Node implements SerializerSymbol.DependencyVisitor {
        @Nullable
        private final Node parent;
        private final GeneratorType type;
        @Nullable
        private final Element debugElement;

        private boolean isStructureNode;

        Node(Node parent, GeneratorType type, Element debugElement) {
            this.parent = parent;
            this.type = type;
            this.debugElement = debugElement;
        }

        private boolean checkParent() {
            Node node = parent;
            while (node != null) {
                if (node.isStructureNode && this.type.typeEquals(node.type)) {
                    // found a cycle!
                    break;
                }
                node = node.parent;
            }
            if (node == null) {
                // no cycle
                return true;
            }
            // `node` has the same type as us, now.
            // walk up the path again, up to the parent with the cycle.
            StringBuilder pathBuilder = new StringBuilder(debugElement == null ? "*" : debugElement.getSimpleName());
            Node pathNode = this;
            while (pathNode != node) {
                pathNode = pathNode.parent;
                assert pathNode != null;
                String elementName = pathNode.debugElement == null ? "*" : pathNode.debugElement.getSimpleName();
                // prepend the node
                pathBuilder.insert(0, elementName + "->");
            }

            warningContext.fail("Circular dependency: " + pathBuilder, debugElement);
            anyFailures = true;
            return false;
        }

        @Override
        public boolean visitStructure() {
            isStructureNode = true;
            return checkParent();
        }

        @Override
        public void visitStructureElement(SerializerSymbol dependencySymbol, GeneratorType dependencyType, @Nullable Element element) {
            visitChild(dependencySymbol, dependencyType, element);
        }

        @Override
        public void visitInjected(GeneratorType dependencyType, boolean provider) {
            if (provider) {
                // we don't care about recursion if it goes through a provider
                return;
            }

            Optional<ClassElement> classDecl = warningContext.getClassElement(dependencyType.getClassElement().getName());
            if (!classDecl.isPresent()) {
                // just ignore the type, nothing we can do.
                return;
            }

            if (classDecl.get().isAnnotationPresent(SerializableBean.class)) {
                visitChild(linker.inlineBean, dependencyType, null);
            } // else, a custom serializer.
        }

        private void visitChild(SerializerSymbol childSymbol, GeneratorType dependencyType, Element element) {
            Node childNode = new Node(this, dependencyType, element);
            childSymbol.visitDependencies(childNode, dependencyType);
        }
    }
}
