package io.micronaut.json


import spock.lang.Specification

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class TypeInferenceSpec extends Specification {
    void "test findParameterization base case"() {
        expect:
        TypeInference.findParameterization(String.class, Object.class) == Object.class
        TypeInference.findParameterization(CharSequence.class, Object.class) == Object.class
        TypeInference.findParameterization(int.class, Object.class) == null
        TypeInference.findParameterization(ArrayList.class, List.class) == List.class // no unresolved type arguments are returned
    }

    void "test findParameterization simple"() {
        expect:
        TypeInference.findParameterization(Simple.class, List.class).typeName == 'java.util.List<java.lang.String>'
        TypeInference.findParameterization(Simple.class, Iterable.class).typeName == 'java.lang.Iterable<java.lang.String>'
    }

    void "test isAssignable simple"() {
        expect:
        TypeInference.isAssignableFrom(new GenericTypeToken<List<String>>() {}.type, Simple.class)
        TypeInference.isAssignableFrom(new GenericTypeToken<Iterable<String>>() {}.type, Simple.class)
        TypeInference.isAssignableFrom(new GenericTypeToken<Iterable<? extends String>>() {}.type, Simple.class)
        TypeInference.isAssignableFrom(new GenericTypeToken<Iterable<? super String>>() {}.type, Simple.class)
        !TypeInference.isAssignableFrom(new GenericTypeToken<List<Integer>>() {}.type, Simple.class)
        !TypeInference.isAssignableFrom(new GenericTypeToken<List<? super CharSequence>>() {}.type, Simple.class)
    }

    private static abstract class GenericTypeToken<T> {
        Type type

        GenericTypeToken() {
            Type parameterization = TypeInference.findParameterization(TypeInference.parameterizeWithFreeVariables(getClass()), GenericTypeToken.class)
            assert parameterization != null
            this.type = ((ParameterizedType) parameterization).getActualTypeArguments()[0]
        }
    }

    static abstract class Simple implements List<String> {}

    void "test findParameterization wildcard"() {
        expect:
        TypeInference.findParameterization(WildcardExt.class, Iterable.class).typeName == 'java.lang.Iterable<java.util.List<? extends java.lang.String>>'
    }

    static abstract class WildcardBase<T> implements List<List<? extends T>> {}
    static abstract class WildcardExt extends WildcardBase<String> {}

    void "test findParameterization wildcard super"() {
        expect:
        TypeInference.findParameterization(WildcardSuperExt.class, Iterable.class).typeName == 'java.lang.Iterable<java.util.List<? super java.lang.String>>'
    }

    static abstract class WildcardSuperBase<T> implements List<List<? super T>> {}
    static abstract class WildcardSuperExt extends WildcardSuperBase<String> {}

    void "test findParameterization inner"() {
        expect:
        // this class is in src/test/java, because it's not valid groovy
        TypeInference.findParameterization(InnerOuter.InnerExt.class, Map.class).typeName == 'java.util.Map<java.lang.String, java.lang.Integer>'
    }

    void "test isAssignable inner"() {
        expect:
        // this class is in src/test/java, because it's not valid groovy
        TypeInference.isAssignableFrom(new GenericTypeToken<Map<String, Integer>>() {}.type, InnerOuter.InnerExt.class)
        TypeInference.isAssignableFrom(new GenericTypeToken<Map<String, ? extends Number>>() {}.type, InnerOuter.InnerExt.class)
        TypeInference.isAssignableFrom(new GenericTypeToken<Map<String, ? super Integer>>() {}.type, InnerOuter.InnerExt.class)
        TypeInference.isAssignableFrom(InnerOuter.COMPATIBLE_TYPE, InnerOuter.InnerExt.class)
        !TypeInference.isAssignableFrom(InnerOuter.INCOMPATIBLE_TYPE, InnerOuter.InnerExt.class)
        !TypeInference.isAssignableFrom(new GenericTypeToken<Map<String, Long>>() {}.type, InnerOuter.InnerExt.class)
    }

    void "test findParameterization generic type var array"() {
        given:
        def genericArrayType = GenericTypeFactory.makeArrayType(HasTypeVar.getTypeParameters()[0])

        expect:
        TypeInference.findParameterization(genericArrayType, Object[]) != null
        TypeInference.findParameterization(genericArrayType, CharSequence[]) != null
        TypeInference.findParameterization(genericArrayType, String[]) != null
        TypeInference.findParameterization(genericArrayType, Serializable) != null
    }

    static class HasTypeVar<T extends String> {}

    void "test findParameterization parameterized type array"() {
        expect:
        TypeInference.findParameterization(new GenericTypeToken<List<String>[]>() {}.getType(), Iterable[]).typeName == 'java.lang.Iterable<java.lang.String>[]'
    }

    void "test findParameterization normal array"() {
        expect:
        TypeInference.findParameterization(String[], CharSequence[]) == CharSequence[]
        TypeInference.findParameterization(String[], Object[]) == Object[]
        TypeInference.findParameterization(String[], Number[]) == null
        TypeInference.findParameterization(String[], Serializable) == Serializable
    }

    void "test findParameterization from normal array to generic"() {
        expect:
        TypeInference.findParameterization(String[], Comparable[]).typeName == 'java.lang.Comparable<java.lang.String>[]'
    }
}
