package io.micronaut.json.generator.symbol;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

class ClassElementUtil {
    static Optional<ClassElement> findParameterization(ClassElement element, ClassElement parent) {
        if (element.isArray()) {
            if (!parent.isArray()) {
                String typeName = parent.getName();
                if (typeName.equals(Object.class.getName()) || typeName.equals(Cloneable.class.getName()) || typeName.equals(Serializable.class.getName())) {
                    return Optional.of(parent);
                } else {
                    return Optional.empty();
                }
            } else {
                ClassElement component = element.fromArray();
                ClassElement parentComponent = parent.fromArray();
                return ClassElementUtil.findParameterization(component, parentComponent).map(ClassElement::toArray);
            }
        } else {
            if (parent.isArray()) {
                return Optional.empty();
            } else if (element.getName().equals(parent.getName())) {
                return Optional.of(element);
            } else if (parent.getName().equals("java.lang.Object")) {
                // special case Object, because Object is also a supertype of interfaces but does not appear in
                // getSupertype for those
                return Optional.of(parent);
            } else {
                Stream<ClassElement> superCandidates = element.getSuperType().map(Stream::of).orElseGet(Stream::empty);
                if (parent.isInterface()) {
                    superCandidates = Stream.concat(superCandidates, element.getInterfaces().stream());
                }
                List<? extends GenericPlaceholderElement> declaredTypeVariables = element.getDeclaredGenericPlaceholders();
                if (!declaredTypeVariables.isEmpty()) {
                    List<? extends ClassElement> boundTypeArguments = element.getBoundGenericTypes();
                    // replace any free variables in the supertypes with our bound variables
                    superCandidates = superCandidates.map(sup -> sup.foldBoundGenericTypes(type -> {
                        if (type == null || !type.isGenericPlaceholder()) {
                            return type;
                        }
                        GenericPlaceholderElement variable = (GenericPlaceholderElement) type;
                        // is the variable actually declared on our class?
                        Optional<io.micronaut.inject.ast.Element> declaringElement = variable.getDeclaringElement();
                        if (!declaringElement.isPresent() || !(declaringElement.get() instanceof ClassElement) || !declaringElement.get().getName().equals(element.getName())) {
                            return type;
                        }
                        // find a declared variable of that name
                        for (int i = 0; i < declaredTypeVariables.size(); i++) {
                            GenericPlaceholderElement candidate = declaredTypeVariables.get(i);
                            if (candidate.getVariableName().equals(variable.getVariableName())) {
                                // found, replace it with our bound value
                                if (i < boundTypeArguments.size()) {
                                    return boundTypeArguments.get(i);
                                } else {
                                    // or, if we're a raw type, make the type raw too. Returning null from this lambda
                                    // signals that any surrounding types should be made raw
                                    return null;
                                }
                            }
                        }
                        // no variable of that name, weird
                        return type;
                    }));
                }
                return superCandidates.map(sup -> findParameterization(sup, parent))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findAny();
            }
        }
    }
}
