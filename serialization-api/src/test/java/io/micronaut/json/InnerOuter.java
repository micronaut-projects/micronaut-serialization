package io.micronaut.json;

import java.lang.reflect.Type;
import java.util.Map;

class InnerOuter<T> {
    abstract class Inner<U> implements Map<T, U> {}

    static final Type INCOMPATIBLE_TYPE = GenericTypeFactory.makeParameterizedTypeWithOwner(
            GenericTypeFactory.makeParameterizedTypeWithOwner(null, InnerOuter.class, String.class),
            InnerOuter.Inner.class,
            Number.class
    );
    static final Type COMPATIBLE_TYPE = GenericTypeFactory.makeParameterizedTypeWithOwner(
            GenericTypeFactory.makeParameterizedTypeWithOwner(null, InnerOuter.class, String.class),
            InnerOuter.Inner.class,
            Integer.class
    );

    abstract class InnerExt extends InnerOuter<String>.Inner<Integer> {}
}