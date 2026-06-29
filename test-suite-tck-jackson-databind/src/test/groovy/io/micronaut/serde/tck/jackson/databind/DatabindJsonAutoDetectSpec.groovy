package io.micronaut.serde.tck.jackson.databind

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.serde.jackson.JsonAutoDetectSpec

class DatabindJsonAutoDetectSpec extends JsonAutoDetectSpec {

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        // Disable Micronaut annotation processors
        return [new TypeElementVisitor() {
            @Override
            void visitClass(ClassElement element, VisitorContext context) {
            }
        }]
    }
}
