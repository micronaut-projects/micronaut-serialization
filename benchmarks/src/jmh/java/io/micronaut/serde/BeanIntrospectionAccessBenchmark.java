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
package io.micronaut.serde;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanReadProperty;
import io.micronaut.core.beans.BeanWriteProperty;
import io.micronaut.core.beans.UnsafeBeanReadProperty;
import io.micronaut.core.beans.UnsafeBeanWriteProperty;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/**
 * Isolates Micronaut Core introspection property access from parser/encoder work.
 */
public class BeanIntrospectionAccessBenchmark {

    private static final String[] PROPERTY_NAMES = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j"};
    private static final String[] OBJECT_VALUES = {
        "value-1000",
        "value-9000000123",
        "value-true",
        "value-123.456",
        "value-2000",
        "value-9000000456",
        "value-false",
        "value-789.123",
        "value-3000",
        "value-9000000789"
    };

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public long read(Holder holder) throws Throwable {
        return holder.read();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object write(Holder holder) throws Throwable {
        return holder.write();
    }

    @State(Scope.Thread)
    public static class Holder {

        @Param({
            "DIRECT",
            "METHOD_HANDLE",
            "UNSAFE_INTROSPECTION"
        })
        Access access = Access.DIRECT;

        @Param({
            "CONSTRUCTOR",
            "GETTER_SETTER",
            "FIELD"
        })
        PropertyValueKindBenchmark.Shape shape = PropertyValueKindBenchmark.Shape.CONSTRUCTOR;

        @Param({
            "PRIMITIVE",
            "BOXED",
            "OBJECT"
        })
        PropertyValueKindBenchmark.ValueKind valueKind = PropertyValueKindBenchmark.ValueKind.PRIMITIVE;

        private BeanIntrospection<?> introspection;
        private MethodHandle[] readHandles;
        private MethodHandle[] writeHandles;
        private MethodHandle constructorHandle;
        private UnsafeBeanReadProperty<Object, Object>[] readProperties;
        private UnsafeBeanWriteProperty<Object, Object>[] writeProperties;
        private Object value;
        private int seed;

        @Setup
        public void setUp() throws Exception {
            Class<?> type = rawType(valueKind, shape);
            introspection = BeanIntrospection.getIntrospection(type);
            readHandles = readHandles(type, valueKind, shape);
            if (shape == PropertyValueKindBenchmark.Shape.CONSTRUCTOR) {
                constructorHandle = constructorHandle(type, valueKind);
            } else {
                writeHandles = writeHandles(type, valueKind, shape);
            }
            readProperties = readProperties(introspection);
            writeProperties = shape == PropertyValueKindBenchmark.Shape.CONSTRUCTOR ? null : writeProperties(introspection);
            value = directWrite(valueKind, shape, 0, null);
        }

        long read() throws Throwable {
            return switch (access) {
                case DIRECT -> directRead(valueKind, shape, value);
                case METHOD_HANDLE -> methodHandleRead(valueKind, shape, value, readHandles);
                case UNSAFE_INTROSPECTION -> unsafeRead(valueKind, value, readProperties);
            };
        }

        Object write() throws Throwable {
            int next = ++seed;
            return switch (access) {
                case DIRECT -> {
                    value = directWrite(valueKind, shape, next, value);
                    yield value;
                }
                case METHOD_HANDLE -> {
                    value = methodHandleWrite(valueKind, shape, next, value, constructorHandle, writeHandles);
                    yield value;
                }
                case UNSAFE_INTROSPECTION -> {
                    value = unsafeWrite(valueKind, shape, next, value, introspection, writeProperties);
                    yield value;
                }
            };
        }

        private static MethodHandle[] readHandles(Class<?> type,
                                                  PropertyValueKindBenchmark.ValueKind valueKind,
                                                  PropertyValueKindBenchmark.Shape shape) throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?>[] propertyTypes = propertyTypes(valueKind);
            MethodHandle[] handles = new MethodHandle[PROPERTY_NAMES.length];
            for (int i = 0; i < PROPERTY_NAMES.length; i++) {
                if (shape == PropertyValueKindBenchmark.Shape.FIELD) {
                    handles[i] = lookup.findGetter(type, PROPERTY_NAMES[i], propertyTypes[i]);
                } else {
                    handles[i] = lookup.findVirtual(type, readMethodName(valueKind, shape, i), MethodType.methodType(propertyTypes[i]));
                }
            }
            return handles;
        }

        private static MethodHandle[] writeHandles(Class<?> type,
                                                   PropertyValueKindBenchmark.ValueKind valueKind,
                                                   PropertyValueKindBenchmark.Shape shape) throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?>[] propertyTypes = propertyTypes(valueKind);
            MethodHandle[] handles = new MethodHandle[PROPERTY_NAMES.length];
            for (int i = 0; i < PROPERTY_NAMES.length; i++) {
                if (shape == PropertyValueKindBenchmark.Shape.FIELD) {
                    handles[i] = lookup.findSetter(type, PROPERTY_NAMES[i], propertyTypes[i]);
                } else {
                    handles[i] = lookup.findVirtual(type, setterName(i), MethodType.methodType(void.class, propertyTypes[i]));
                }
            }
            return handles;
        }

        private static MethodHandle constructorHandle(Class<?> type,
                                                     PropertyValueKindBenchmark.ValueKind valueKind) throws NoSuchMethodException, IllegalAccessException {
            return MethodHandles.publicLookup().findConstructor(type, MethodType.methodType(void.class, propertyTypes(valueKind)));
        }

        private static String readMethodName(PropertyValueKindBenchmark.ValueKind valueKind,
                                             PropertyValueKindBenchmark.Shape shape,
                                             int index) {
            if (shape == PropertyValueKindBenchmark.Shape.CONSTRUCTOR) {
                return PROPERTY_NAMES[index];
            }
            if (valueKind == PropertyValueKindBenchmark.ValueKind.PRIMITIVE && (index == 2 || index == 6)) {
                return "is" + upperPropertyName(index);
            }
            return "get" + upperPropertyName(index);
        }

        private static String setterName(int index) {
            return "set" + upperPropertyName(index);
        }

        private static String upperPropertyName(int index) {
            return PROPERTY_NAMES[index].toUpperCase();
        }

        private static IllegalStateException unsupportedValueKind(PropertyValueKindBenchmark.ValueKind valueKind) {
            return new IllegalStateException("Unsupported value kind for introspection benchmark: " + valueKind);
        }

        private static Class<?>[] propertyTypes(PropertyValueKindBenchmark.ValueKind valueKind) {
            return switch (valueKind) {
                case PRIMITIVE -> new Class<?>[] {
                    int.class,
                    long.class,
                    boolean.class,
                    double.class,
                    int.class,
                    long.class,
                    boolean.class,
                    double.class,
                    int.class,
                    long.class
                };
                case BOXED -> new Class<?>[] {
                    Integer.class,
                    Long.class,
                    Boolean.class,
                    Double.class,
                    Integer.class,
                    Long.class,
                    Boolean.class,
                    Double.class,
                    Integer.class,
                    Long.class
                };
                case OBJECT -> new Class<?>[] {
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class
                };
                default -> throw unsupportedValueKind(valueKind);
            };
        }

        @SuppressWarnings("unchecked")
        private static UnsafeBeanReadProperty<Object, Object>[] readProperties(BeanIntrospection<?> introspection) {
            List<? extends BeanReadProperty<?, Object>> readProperties = introspection.getBeanReadProperties();
            UnsafeBeanReadProperty<Object, Object>[] properties = new UnsafeBeanReadProperty[PROPERTY_NAMES.length];
            for (int i = 0; i < PROPERTY_NAMES.length; i++) {
                properties[i] = readProperty(readProperties, PROPERTY_NAMES[i]);
            }
            return properties;
        }

        @SuppressWarnings("unchecked")
        private static UnsafeBeanWriteProperty<Object, Object>[] writeProperties(BeanIntrospection<?> introspection) {
            List<? extends BeanWriteProperty<?, Object>> writeProperties = introspection.getBeanWriteProperties();
            UnsafeBeanWriteProperty<Object, Object>[] properties = new UnsafeBeanWriteProperty[PROPERTY_NAMES.length];
            for (int i = 0; i < PROPERTY_NAMES.length; i++) {
                properties[i] = writeProperty(writeProperties, PROPERTY_NAMES[i]);
            }
            return properties;
        }

        @SuppressWarnings("unchecked")
        private static UnsafeBeanReadProperty<Object, Object> readProperty(List<? extends BeanReadProperty<?, Object>> properties, String name) {
            for (BeanReadProperty<?, Object> property : properties) {
                if (property.getName().equals(name)) {
                    return (UnsafeBeanReadProperty<Object, Object>) property;
                }
            }
            throw new IllegalStateException("Missing read property: " + name);
        }

        @SuppressWarnings("unchecked")
        private static UnsafeBeanWriteProperty<Object, Object> writeProperty(List<? extends BeanWriteProperty<?, Object>> properties, String name) {
            for (BeanWriteProperty<?, Object> property : properties) {
                if (property.getName().equals(name)) {
                    return (UnsafeBeanWriteProperty<Object, Object>) property;
                }
            }
            throw new IllegalStateException("Missing write property: " + name);
        }

        private static Class<?> rawType(PropertyValueKindBenchmark.ValueKind valueKind, PropertyValueKindBenchmark.Shape shape) {
            return switch (valueKind) {
                case PRIMITIVE -> switch (shape) {
                    case CONSTRUCTOR -> PropertyValueKindBenchmark.PrimitiveConstructorShape.class;
                    case GETTER_SETTER -> PropertyValueKindBenchmark.PrimitiveGetterSetterShape.class;
                    case FIELD -> PropertyValueKindBenchmark.PrimitiveFieldShape.class;
                };
                case BOXED -> switch (shape) {
                    case CONSTRUCTOR -> PropertyValueKindBenchmark.BoxedConstructorShape.class;
                    case GETTER_SETTER -> PropertyValueKindBenchmark.BoxedGetterSetterShape.class;
                    case FIELD -> PropertyValueKindBenchmark.BoxedFieldShape.class;
                };
                case OBJECT -> switch (shape) {
                    case CONSTRUCTOR -> PropertyValueKindBenchmark.ObjectConstructorShape.class;
                    case GETTER_SETTER -> PropertyValueKindBenchmark.ObjectGetterSetterShape.class;
                    case FIELD -> PropertyValueKindBenchmark.ObjectFieldShape.class;
                };
                default -> throw unsupportedValueKind(valueKind);
            };
        }

        private static long directRead(PropertyValueKindBenchmark.ValueKind valueKind, PropertyValueKindBenchmark.Shape shape, Object bean) {
            return switch (valueKind) {
                case PRIMITIVE -> directReadPrimitive(shape, bean);
                case BOXED -> directReadBoxed(shape, bean);
                case OBJECT -> directReadObject(shape, bean);
                default -> throw unsupportedValueKind(valueKind);
            };
        }

        private static long directReadPrimitive(PropertyValueKindBenchmark.Shape shape, Object bean) {
            return switch (shape) {
                case CONSTRUCTOR -> {
                    PropertyValueKindBenchmark.PrimitiveConstructorShape value = (PropertyValueKindBenchmark.PrimitiveConstructorShape) bean;
                    yield primitiveHash(value.a(), value.b(), value.c(), value.d(), value.e(), value.f(), value.g(), value.h(), value.i(), value.j());
                }
                case GETTER_SETTER -> {
                    PropertyValueKindBenchmark.PrimitiveGetterSetterShape value = (PropertyValueKindBenchmark.PrimitiveGetterSetterShape) bean;
                    yield primitiveHash(value.getA(), value.getB(), value.isC(), value.getD(), value.getE(), value.getF(), value.isG(), value.getH(), value.getI(), value.getJ());
                }
                case FIELD -> {
                    PropertyValueKindBenchmark.PrimitiveFieldShape value = (PropertyValueKindBenchmark.PrimitiveFieldShape) bean;
                    yield primitiveHash(value.a, value.b, value.c, value.d, value.e, value.f, value.g, value.h, value.i, value.j);
                }
            };
        }

        private static long directReadBoxed(PropertyValueKindBenchmark.Shape shape, Object bean) {
            return switch (shape) {
                case CONSTRUCTOR -> {
                    PropertyValueKindBenchmark.BoxedConstructorShape value = (PropertyValueKindBenchmark.BoxedConstructorShape) bean;
                    yield boxedHash(value.a(), value.b(), value.c(), value.d(), value.e(), value.f(), value.g(), value.h(), value.i(), value.j());
                }
                case GETTER_SETTER -> {
                    PropertyValueKindBenchmark.BoxedGetterSetterShape value = (PropertyValueKindBenchmark.BoxedGetterSetterShape) bean;
                    yield boxedHash(value.getA(), value.getB(), value.getC(), value.getD(), value.getE(), value.getF(), value.getG(), value.getH(), value.getI(), value.getJ());
                }
                case FIELD -> {
                    PropertyValueKindBenchmark.BoxedFieldShape value = (PropertyValueKindBenchmark.BoxedFieldShape) bean;
                    yield boxedHash(value.a, value.b, value.c, value.d, value.e, value.f, value.g, value.h, value.i, value.j);
                }
            };
        }

        private static long directReadObject(PropertyValueKindBenchmark.Shape shape, Object bean) {
            return switch (shape) {
                case CONSTRUCTOR -> {
                    PropertyValueKindBenchmark.ObjectConstructorShape value = (PropertyValueKindBenchmark.ObjectConstructorShape) bean;
                    yield objectHash(value.a(), value.b(), value.c(), value.d(), value.e(), value.f(), value.g(), value.h(), value.i(), value.j());
                }
                case GETTER_SETTER -> {
                    PropertyValueKindBenchmark.ObjectGetterSetterShape value = (PropertyValueKindBenchmark.ObjectGetterSetterShape) bean;
                    yield objectHash(value.getA(), value.getB(), value.getC(), value.getD(), value.getE(), value.getF(), value.getG(), value.getH(), value.getI(), value.getJ());
                }
                case FIELD -> {
                    PropertyValueKindBenchmark.ObjectFieldShape value = (PropertyValueKindBenchmark.ObjectFieldShape) bean;
                    yield objectHash(value.a, value.b, value.c, value.d, value.e, value.f, value.g, value.h, value.i, value.j);
                }
            };
        }

        private static long unsafeRead(PropertyValueKindBenchmark.ValueKind valueKind, Object bean, UnsafeBeanReadProperty<Object, Object>[] properties) {
            return switch (valueKind) {
                case PRIMITIVE -> primitiveHash(
                    (Integer) properties[0].getUnsafe(bean),
                    (Long) properties[1].getUnsafe(bean),
                    (Boolean) properties[2].getUnsafe(bean),
                    (Double) properties[3].getUnsafe(bean),
                    (Integer) properties[4].getUnsafe(bean),
                    (Long) properties[5].getUnsafe(bean),
                    (Boolean) properties[6].getUnsafe(bean),
                    (Double) properties[7].getUnsafe(bean),
                    (Integer) properties[8].getUnsafe(bean),
                    (Long) properties[9].getUnsafe(bean));
                case BOXED -> boxedHash(
                    (Integer) properties[0].getUnsafe(bean),
                    (Long) properties[1].getUnsafe(bean),
                    (Boolean) properties[2].getUnsafe(bean),
                    (Double) properties[3].getUnsafe(bean),
                    (Integer) properties[4].getUnsafe(bean),
                    (Long) properties[5].getUnsafe(bean),
                    (Boolean) properties[6].getUnsafe(bean),
                    (Double) properties[7].getUnsafe(bean),
                    (Integer) properties[8].getUnsafe(bean),
                    (Long) properties[9].getUnsafe(bean));
                case OBJECT -> objectHash(
                    (String) properties[0].getUnsafe(bean),
                    (String) properties[1].getUnsafe(bean),
                    (String) properties[2].getUnsafe(bean),
                    (String) properties[3].getUnsafe(bean),
                    (String) properties[4].getUnsafe(bean),
                    (String) properties[5].getUnsafe(bean),
                    (String) properties[6].getUnsafe(bean),
                    (String) properties[7].getUnsafe(bean),
                    (String) properties[8].getUnsafe(bean),
                    (String) properties[9].getUnsafe(bean));
                default -> throw unsupportedValueKind(valueKind);
            };
        }

        private static long methodHandleRead(PropertyValueKindBenchmark.ValueKind valueKind,
                                             PropertyValueKindBenchmark.Shape shape,
                                             Object bean,
                                             MethodHandle[] handles) throws Throwable {
            return switch (valueKind) {
                case PRIMITIVE -> methodHandleReadPrimitive(shape, bean, handles);
                case BOXED -> methodHandleReadBoxed(shape, bean, handles);
                case OBJECT -> methodHandleReadObject(shape, bean, handles);
                default -> throw unsupportedValueKind(valueKind);
            };
        }

        private static long methodHandleReadPrimitive(PropertyValueKindBenchmark.Shape shape, Object bean, MethodHandle[] handles) throws Throwable {
            return switch (shape) {
                case CONSTRUCTOR -> {
                    PropertyValueKindBenchmark.PrimitiveConstructorShape value = (PropertyValueKindBenchmark.PrimitiveConstructorShape) bean;
                    yield primitiveHash(
                        (int) handles[0].invokeExact(value),
                        (long) handles[1].invokeExact(value),
                        (boolean) handles[2].invokeExact(value),
                        (double) handles[3].invokeExact(value),
                        (int) handles[4].invokeExact(value),
                        (long) handles[5].invokeExact(value),
                        (boolean) handles[6].invokeExact(value),
                        (double) handles[7].invokeExact(value),
                        (int) handles[8].invokeExact(value),
                        (long) handles[9].invokeExact(value));
                }
                case GETTER_SETTER -> {
                    PropertyValueKindBenchmark.PrimitiveGetterSetterShape value = (PropertyValueKindBenchmark.PrimitiveGetterSetterShape) bean;
                    yield primitiveHash(
                        (int) handles[0].invokeExact(value),
                        (long) handles[1].invokeExact(value),
                        (boolean) handles[2].invokeExact(value),
                        (double) handles[3].invokeExact(value),
                        (int) handles[4].invokeExact(value),
                        (long) handles[5].invokeExact(value),
                        (boolean) handles[6].invokeExact(value),
                        (double) handles[7].invokeExact(value),
                        (int) handles[8].invokeExact(value),
                        (long) handles[9].invokeExact(value));
                }
                case FIELD -> {
                    PropertyValueKindBenchmark.PrimitiveFieldShape value = (PropertyValueKindBenchmark.PrimitiveFieldShape) bean;
                    yield primitiveHash(
                        (int) handles[0].invokeExact(value),
                        (long) handles[1].invokeExact(value),
                        (boolean) handles[2].invokeExact(value),
                        (double) handles[3].invokeExact(value),
                        (int) handles[4].invokeExact(value),
                        (long) handles[5].invokeExact(value),
                        (boolean) handles[6].invokeExact(value),
                        (double) handles[7].invokeExact(value),
                        (int) handles[8].invokeExact(value),
                        (long) handles[9].invokeExact(value));
                }
            };
        }

        private static long methodHandleReadBoxed(PropertyValueKindBenchmark.Shape shape, Object bean, MethodHandle[] handles) throws Throwable {
            return switch (shape) {
                case CONSTRUCTOR -> {
                    PropertyValueKindBenchmark.BoxedConstructorShape value = (PropertyValueKindBenchmark.BoxedConstructorShape) bean;
                    yield boxedHash(
                        (Integer) handles[0].invokeExact(value),
                        (Long) handles[1].invokeExact(value),
                        (Boolean) handles[2].invokeExact(value),
                        (Double) handles[3].invokeExact(value),
                        (Integer) handles[4].invokeExact(value),
                        (Long) handles[5].invokeExact(value),
                        (Boolean) handles[6].invokeExact(value),
                        (Double) handles[7].invokeExact(value),
                        (Integer) handles[8].invokeExact(value),
                        (Long) handles[9].invokeExact(value));
                }
                case GETTER_SETTER -> {
                    PropertyValueKindBenchmark.BoxedGetterSetterShape value = (PropertyValueKindBenchmark.BoxedGetterSetterShape) bean;
                    yield boxedHash(
                        (Integer) handles[0].invokeExact(value),
                        (Long) handles[1].invokeExact(value),
                        (Boolean) handles[2].invokeExact(value),
                        (Double) handles[3].invokeExact(value),
                        (Integer) handles[4].invokeExact(value),
                        (Long) handles[5].invokeExact(value),
                        (Boolean) handles[6].invokeExact(value),
                        (Double) handles[7].invokeExact(value),
                        (Integer) handles[8].invokeExact(value),
                        (Long) handles[9].invokeExact(value));
                }
                case FIELD -> {
                    PropertyValueKindBenchmark.BoxedFieldShape value = (PropertyValueKindBenchmark.BoxedFieldShape) bean;
                    yield boxedHash(
                        (Integer) handles[0].invokeExact(value),
                        (Long) handles[1].invokeExact(value),
                        (Boolean) handles[2].invokeExact(value),
                        (Double) handles[3].invokeExact(value),
                        (Integer) handles[4].invokeExact(value),
                        (Long) handles[5].invokeExact(value),
                        (Boolean) handles[6].invokeExact(value),
                        (Double) handles[7].invokeExact(value),
                        (Integer) handles[8].invokeExact(value),
                        (Long) handles[9].invokeExact(value));
                }
            };
        }

        private static long methodHandleReadObject(PropertyValueKindBenchmark.Shape shape, Object bean, MethodHandle[] handles) throws Throwable {
            return switch (shape) {
                case CONSTRUCTOR -> {
                    PropertyValueKindBenchmark.ObjectConstructorShape value = (PropertyValueKindBenchmark.ObjectConstructorShape) bean;
                    yield objectHash(
                        (String) handles[0].invokeExact(value),
                        (String) handles[1].invokeExact(value),
                        (String) handles[2].invokeExact(value),
                        (String) handles[3].invokeExact(value),
                        (String) handles[4].invokeExact(value),
                        (String) handles[5].invokeExact(value),
                        (String) handles[6].invokeExact(value),
                        (String) handles[7].invokeExact(value),
                        (String) handles[8].invokeExact(value),
                        (String) handles[9].invokeExact(value));
                }
                case GETTER_SETTER -> {
                    PropertyValueKindBenchmark.ObjectGetterSetterShape value = (PropertyValueKindBenchmark.ObjectGetterSetterShape) bean;
                    yield objectHash(
                        (String) handles[0].invokeExact(value),
                        (String) handles[1].invokeExact(value),
                        (String) handles[2].invokeExact(value),
                        (String) handles[3].invokeExact(value),
                        (String) handles[4].invokeExact(value),
                        (String) handles[5].invokeExact(value),
                        (String) handles[6].invokeExact(value),
                        (String) handles[7].invokeExact(value),
                        (String) handles[8].invokeExact(value),
                        (String) handles[9].invokeExact(value));
                }
                case FIELD -> {
                    PropertyValueKindBenchmark.ObjectFieldShape value = (PropertyValueKindBenchmark.ObjectFieldShape) bean;
                    yield objectHash(
                        (String) handles[0].invokeExact(value),
                        (String) handles[1].invokeExact(value),
                        (String) handles[2].invokeExact(value),
                        (String) handles[3].invokeExact(value),
                        (String) handles[4].invokeExact(value),
                        (String) handles[5].invokeExact(value),
                        (String) handles[6].invokeExact(value),
                        (String) handles[7].invokeExact(value),
                        (String) handles[8].invokeExact(value),
                        (String) handles[9].invokeExact(value));
                }
            };
        }

        private static Object directWrite(PropertyValueKindBenchmark.ValueKind valueKind,
                                          PropertyValueKindBenchmark.Shape shape,
                                          int seed,
                                          Object current) {
            return switch (valueKind) {
                case PRIMITIVE -> directWritePrimitive(shape, seed, current);
                case BOXED -> directWriteBoxed(shape, seed, current);
                case OBJECT -> directWriteObject(shape, current);
                default -> throw unsupportedValueKind(valueKind);
            };
        }

        private static Object directWritePrimitive(PropertyValueKindBenchmark.Shape shape, int seed, Object current) {
            int a = 1000 + seed;
            long b = 9_000_000_123L + seed;
            boolean c = (seed & 1) == 0;
            double d = 123.456D + seed;
            int e = 2000 + seed;
            long f = 9_000_000_456L + seed;
            boolean g = (seed & 1) != 0;
            double h = 789.123D + seed;
            int i = 3000 + seed;
            long j = 9_000_000_789L + seed;
            return switch (shape) {
                case CONSTRUCTOR -> new PropertyValueKindBenchmark.PrimitiveConstructorShape(a, b, c, d, e, f, g, h, i, j);
                case GETTER_SETTER -> {
                    PropertyValueKindBenchmark.PrimitiveGetterSetterShape value = current == null ? new PropertyValueKindBenchmark.PrimitiveGetterSetterShape() : (PropertyValueKindBenchmark.PrimitiveGetterSetterShape) current;
                    value.setA(a);
                    value.setB(b);
                    value.setC(c);
                    value.setD(d);
                    value.setE(e);
                    value.setF(f);
                    value.setG(g);
                    value.setH(h);
                    value.setI(i);
                    value.setJ(j);
                    yield value;
                }
                case FIELD -> {
                    PropertyValueKindBenchmark.PrimitiveFieldShape value = current == null ? new PropertyValueKindBenchmark.PrimitiveFieldShape() : (PropertyValueKindBenchmark.PrimitiveFieldShape) current;
                    value.a = a;
                    value.b = b;
                    value.c = c;
                    value.d = d;
                    value.e = e;
                    value.f = f;
                    value.g = g;
                    value.h = h;
                    value.i = i;
                    value.j = j;
                    yield value;
                }
            };
        }

        private static Object directWriteBoxed(PropertyValueKindBenchmark.Shape shape, int seed, Object current) {
            Integer a = 1000 + seed;
            Long b = 9_000_000_123L + seed;
            Boolean c = (seed & 1) == 0;
            Double d = 123.456D + seed;
            Integer e = 2000 + seed;
            Long f = 9_000_000_456L + seed;
            Boolean g = (seed & 1) != 0;
            Double h = 789.123D + seed;
            Integer i = 3000 + seed;
            Long j = 9_000_000_789L + seed;
            return switch (shape) {
                case CONSTRUCTOR -> new PropertyValueKindBenchmark.BoxedConstructorShape(a, b, c, d, e, f, g, h, i, j);
                case GETTER_SETTER -> {
                    PropertyValueKindBenchmark.BoxedGetterSetterShape value = current == null ? new PropertyValueKindBenchmark.BoxedGetterSetterShape() : (PropertyValueKindBenchmark.BoxedGetterSetterShape) current;
                    value.setA(a);
                    value.setB(b);
                    value.setC(c);
                    value.setD(d);
                    value.setE(e);
                    value.setF(f);
                    value.setG(g);
                    value.setH(h);
                    value.setI(i);
                    value.setJ(j);
                    yield value;
                }
                case FIELD -> {
                    PropertyValueKindBenchmark.BoxedFieldShape value = current == null ? new PropertyValueKindBenchmark.BoxedFieldShape() : (PropertyValueKindBenchmark.BoxedFieldShape) current;
                    value.a = a;
                    value.b = b;
                    value.c = c;
                    value.d = d;
                    value.e = e;
                    value.f = f;
                    value.g = g;
                    value.h = h;
                    value.i = i;
                    value.j = j;
                    yield value;
                }
            };
        }

        private static Object directWriteObject(PropertyValueKindBenchmark.Shape shape, Object current) {
            return switch (shape) {
                case CONSTRUCTOR -> new PropertyValueKindBenchmark.ObjectConstructorShape(
                    OBJECT_VALUES[0],
                    OBJECT_VALUES[1],
                    OBJECT_VALUES[2],
                    OBJECT_VALUES[3],
                    OBJECT_VALUES[4],
                    OBJECT_VALUES[5],
                    OBJECT_VALUES[6],
                    OBJECT_VALUES[7],
                    OBJECT_VALUES[8],
                    OBJECT_VALUES[9]);
                case GETTER_SETTER -> {
                    PropertyValueKindBenchmark.ObjectGetterSetterShape value = current == null ? new PropertyValueKindBenchmark.ObjectGetterSetterShape() : (PropertyValueKindBenchmark.ObjectGetterSetterShape) current;
                    value.setA(OBJECT_VALUES[0]);
                    value.setB(OBJECT_VALUES[1]);
                    value.setC(OBJECT_VALUES[2]);
                    value.setD(OBJECT_VALUES[3]);
                    value.setE(OBJECT_VALUES[4]);
                    value.setF(OBJECT_VALUES[5]);
                    value.setG(OBJECT_VALUES[6]);
                    value.setH(OBJECT_VALUES[7]);
                    value.setI(OBJECT_VALUES[8]);
                    value.setJ(OBJECT_VALUES[9]);
                    yield value;
                }
                case FIELD -> {
                    PropertyValueKindBenchmark.ObjectFieldShape value = current == null ? new PropertyValueKindBenchmark.ObjectFieldShape() : (PropertyValueKindBenchmark.ObjectFieldShape) current;
                    value.a = OBJECT_VALUES[0];
                    value.b = OBJECT_VALUES[1];
                    value.c = OBJECT_VALUES[2];
                    value.d = OBJECT_VALUES[3];
                    value.e = OBJECT_VALUES[4];
                    value.f = OBJECT_VALUES[5];
                    value.g = OBJECT_VALUES[6];
                    value.h = OBJECT_VALUES[7];
                    value.i = OBJECT_VALUES[8];
                    value.j = OBJECT_VALUES[9];
                    yield value;
                }
            };
        }

        private static Object unsafeWrite(PropertyValueKindBenchmark.ValueKind valueKind,
                                          PropertyValueKindBenchmark.Shape shape,
                                          int seed,
                                          Object current,
                                          BeanIntrospection<?> introspection,
                                          UnsafeBeanWriteProperty<Object, Object>[] properties) {
            return switch (valueKind) {
                case PRIMITIVE -> unsafeWritePrimitive(shape, seed, current, introspection, properties);
                case BOXED -> unsafeWriteBoxed(shape, seed, current, introspection, properties);
                case OBJECT -> unsafeWriteObject(shape, current, introspection, properties);
                default -> throw unsupportedValueKind(valueKind);
            };
        }

        private static Object unsafeWritePrimitive(PropertyValueKindBenchmark.Shape shape,
                                                   int seed,
                                                   Object current,
                                                   BeanIntrospection<?> introspection,
                                                   UnsafeBeanWriteProperty<Object, Object>[] properties) {
            int a = 1000 + seed;
            long b = 9_000_000_123L + seed;
            boolean c = (seed & 1) == 0;
            double d = 123.456D + seed;
            int e = 2000 + seed;
            long f = 9_000_000_456L + seed;
            boolean g = (seed & 1) != 0;
            double h = 789.123D + seed;
            int i = 3000 + seed;
            long j = 9_000_000_789L + seed;
            if (shape == PropertyValueKindBenchmark.Shape.CONSTRUCTOR) {
                return introspection.instantiate(false, new Object[]{a, b, c, d, e, f, g, h, i, j});
            }
            properties[0].setUnsafe(current, a);
            properties[1].setUnsafe(current, b);
            properties[2].setUnsafe(current, c);
            properties[3].setUnsafe(current, d);
            properties[4].setUnsafe(current, e);
            properties[5].setUnsafe(current, f);
            properties[6].setUnsafe(current, g);
            properties[7].setUnsafe(current, h);
            properties[8].setUnsafe(current, i);
            properties[9].setUnsafe(current, j);
            return current;
        }

        private static Object methodHandleWrite(PropertyValueKindBenchmark.ValueKind valueKind,
                                                PropertyValueKindBenchmark.Shape shape,
                                                int seed,
                                                Object current,
                                                MethodHandle constructorHandle,
                                                MethodHandle[] handles) throws Throwable {
            return switch (valueKind) {
                case PRIMITIVE -> methodHandleWritePrimitive(shape, seed, current, constructorHandle, handles);
                case BOXED -> methodHandleWriteBoxed(shape, seed, current, constructorHandle, handles);
                case OBJECT -> methodHandleWriteObject(shape, current, constructorHandle, handles);
                default -> throw unsupportedValueKind(valueKind);
            };
        }

        private static Object methodHandleWritePrimitive(PropertyValueKindBenchmark.Shape shape,
                                                         int seed,
                                                         Object current,
                                                         MethodHandle constructorHandle,
                                                         MethodHandle[] handles) throws Throwable {
            int a = 1000 + seed;
            long b = 9_000_000_123L + seed;
            boolean c = (seed & 1) == 0;
            double d = 123.456D + seed;
            int e = 2000 + seed;
            long f = 9_000_000_456L + seed;
            boolean g = (seed & 1) != 0;
            double h = 789.123D + seed;
            int i = 3000 + seed;
            long j = 9_000_000_789L + seed;
            return switch (shape) {
                case CONSTRUCTOR -> (PropertyValueKindBenchmark.PrimitiveConstructorShape) constructorHandle.invokeExact(a, b, c, d, e, f, g, h, i, j);
                case GETTER_SETTER -> {
                    PropertyValueKindBenchmark.PrimitiveGetterSetterShape value = current == null ? new PropertyValueKindBenchmark.PrimitiveGetterSetterShape() : (PropertyValueKindBenchmark.PrimitiveGetterSetterShape) current;
                    handles[0].invokeExact(value, a);
                    handles[1].invokeExact(value, b);
                    handles[2].invokeExact(value, c);
                    handles[3].invokeExact(value, d);
                    handles[4].invokeExact(value, e);
                    handles[5].invokeExact(value, f);
                    handles[6].invokeExact(value, g);
                    handles[7].invokeExact(value, h);
                    handles[8].invokeExact(value, i);
                    handles[9].invokeExact(value, j);
                    yield value;
                }
                case FIELD -> {
                    PropertyValueKindBenchmark.PrimitiveFieldShape value = current == null ? new PropertyValueKindBenchmark.PrimitiveFieldShape() : (PropertyValueKindBenchmark.PrimitiveFieldShape) current;
                    handles[0].invokeExact(value, a);
                    handles[1].invokeExact(value, b);
                    handles[2].invokeExact(value, c);
                    handles[3].invokeExact(value, d);
                    handles[4].invokeExact(value, e);
                    handles[5].invokeExact(value, f);
                    handles[6].invokeExact(value, g);
                    handles[7].invokeExact(value, h);
                    handles[8].invokeExact(value, i);
                    handles[9].invokeExact(value, j);
                    yield value;
                }
            };
        }

        private static Object methodHandleWriteBoxed(PropertyValueKindBenchmark.Shape shape,
                                                     int seed,
                                                     Object current,
                                                     MethodHandle constructorHandle,
                                                     MethodHandle[] handles) throws Throwable {
            Integer a = 1000 + seed;
            Long b = 9_000_000_123L + seed;
            Boolean c = (seed & 1) == 0;
            Double d = 123.456D + seed;
            Integer e = 2000 + seed;
            Long f = 9_000_000_456L + seed;
            Boolean g = (seed & 1) != 0;
            Double h = 789.123D + seed;
            Integer i = 3000 + seed;
            Long j = 9_000_000_789L + seed;
            return switch (shape) {
                case CONSTRUCTOR -> (PropertyValueKindBenchmark.BoxedConstructorShape) constructorHandle.invokeExact(a, b, c, d, e, f, g, h, i, j);
                case GETTER_SETTER -> {
                    PropertyValueKindBenchmark.BoxedGetterSetterShape value = current == null ? new PropertyValueKindBenchmark.BoxedGetterSetterShape() : (PropertyValueKindBenchmark.BoxedGetterSetterShape) current;
                    handles[0].invokeExact(value, a);
                    handles[1].invokeExact(value, b);
                    handles[2].invokeExact(value, c);
                    handles[3].invokeExact(value, d);
                    handles[4].invokeExact(value, e);
                    handles[5].invokeExact(value, f);
                    handles[6].invokeExact(value, g);
                    handles[7].invokeExact(value, h);
                    handles[8].invokeExact(value, i);
                    handles[9].invokeExact(value, j);
                    yield value;
                }
                case FIELD -> {
                    PropertyValueKindBenchmark.BoxedFieldShape value = current == null ? new PropertyValueKindBenchmark.BoxedFieldShape() : (PropertyValueKindBenchmark.BoxedFieldShape) current;
                    handles[0].invokeExact(value, a);
                    handles[1].invokeExact(value, b);
                    handles[2].invokeExact(value, c);
                    handles[3].invokeExact(value, d);
                    handles[4].invokeExact(value, e);
                    handles[5].invokeExact(value, f);
                    handles[6].invokeExact(value, g);
                    handles[7].invokeExact(value, h);
                    handles[8].invokeExact(value, i);
                    handles[9].invokeExact(value, j);
                    yield value;
                }
            };
        }

        private static Object methodHandleWriteObject(PropertyValueKindBenchmark.Shape shape,
                                                      Object current,
                                                      MethodHandle constructorHandle,
                                                      MethodHandle[] handles) throws Throwable {
            return switch (shape) {
                case CONSTRUCTOR -> (PropertyValueKindBenchmark.ObjectConstructorShape) constructorHandle.invokeExact(
                    OBJECT_VALUES[0],
                    OBJECT_VALUES[1],
                    OBJECT_VALUES[2],
                    OBJECT_VALUES[3],
                    OBJECT_VALUES[4],
                    OBJECT_VALUES[5],
                    OBJECT_VALUES[6],
                    OBJECT_VALUES[7],
                    OBJECT_VALUES[8],
                    OBJECT_VALUES[9]);
                case GETTER_SETTER -> {
                    PropertyValueKindBenchmark.ObjectGetterSetterShape value = current == null ? new PropertyValueKindBenchmark.ObjectGetterSetterShape() : (PropertyValueKindBenchmark.ObjectGetterSetterShape) current;
                    handles[0].invokeExact(value, OBJECT_VALUES[0]);
                    handles[1].invokeExact(value, OBJECT_VALUES[1]);
                    handles[2].invokeExact(value, OBJECT_VALUES[2]);
                    handles[3].invokeExact(value, OBJECT_VALUES[3]);
                    handles[4].invokeExact(value, OBJECT_VALUES[4]);
                    handles[5].invokeExact(value, OBJECT_VALUES[5]);
                    handles[6].invokeExact(value, OBJECT_VALUES[6]);
                    handles[7].invokeExact(value, OBJECT_VALUES[7]);
                    handles[8].invokeExact(value, OBJECT_VALUES[8]);
                    handles[9].invokeExact(value, OBJECT_VALUES[9]);
                    yield value;
                }
                case FIELD -> {
                    PropertyValueKindBenchmark.ObjectFieldShape value = current == null ? new PropertyValueKindBenchmark.ObjectFieldShape() : (PropertyValueKindBenchmark.ObjectFieldShape) current;
                    handles[0].invokeExact(value, OBJECT_VALUES[0]);
                    handles[1].invokeExact(value, OBJECT_VALUES[1]);
                    handles[2].invokeExact(value, OBJECT_VALUES[2]);
                    handles[3].invokeExact(value, OBJECT_VALUES[3]);
                    handles[4].invokeExact(value, OBJECT_VALUES[4]);
                    handles[5].invokeExact(value, OBJECT_VALUES[5]);
                    handles[6].invokeExact(value, OBJECT_VALUES[6]);
                    handles[7].invokeExact(value, OBJECT_VALUES[7]);
                    handles[8].invokeExact(value, OBJECT_VALUES[8]);
                    handles[9].invokeExact(value, OBJECT_VALUES[9]);
                    yield value;
                }
            };
        }

        private static Object unsafeWriteBoxed(PropertyValueKindBenchmark.Shape shape,
                                               int seed,
                                               Object current,
                                               BeanIntrospection<?> introspection,
                                               UnsafeBeanWriteProperty<Object, Object>[] properties) {
            Integer a = 1000 + seed;
            Long b = 9_000_000_123L + seed;
            Boolean c = (seed & 1) == 0;
            Double d = 123.456D + seed;
            Integer e = 2000 + seed;
            Long f = 9_000_000_456L + seed;
            Boolean g = (seed & 1) != 0;
            Double h = 789.123D + seed;
            Integer i = 3000 + seed;
            Long j = 9_000_000_789L + seed;
            if (shape == PropertyValueKindBenchmark.Shape.CONSTRUCTOR) {
                return introspection.instantiate(false, new Object[]{a, b, c, d, e, f, g, h, i, j});
            }
            properties[0].setUnsafe(current, a);
            properties[1].setUnsafe(current, b);
            properties[2].setUnsafe(current, c);
            properties[3].setUnsafe(current, d);
            properties[4].setUnsafe(current, e);
            properties[5].setUnsafe(current, f);
            properties[6].setUnsafe(current, g);
            properties[7].setUnsafe(current, h);
            properties[8].setUnsafe(current, i);
            properties[9].setUnsafe(current, j);
            return current;
        }

        private static Object unsafeWriteObject(PropertyValueKindBenchmark.Shape shape,
                                                Object current,
                                                BeanIntrospection<?> introspection,
                                                UnsafeBeanWriteProperty<Object, Object>[] properties) {
            if (shape == PropertyValueKindBenchmark.Shape.CONSTRUCTOR) {
                return introspection.instantiate(false, (Object[]) OBJECT_VALUES);
            }
            properties[0].setUnsafe(current, OBJECT_VALUES[0]);
            properties[1].setUnsafe(current, OBJECT_VALUES[1]);
            properties[2].setUnsafe(current, OBJECT_VALUES[2]);
            properties[3].setUnsafe(current, OBJECT_VALUES[3]);
            properties[4].setUnsafe(current, OBJECT_VALUES[4]);
            properties[5].setUnsafe(current, OBJECT_VALUES[5]);
            properties[6].setUnsafe(current, OBJECT_VALUES[6]);
            properties[7].setUnsafe(current, OBJECT_VALUES[7]);
            properties[8].setUnsafe(current, OBJECT_VALUES[8]);
            properties[9].setUnsafe(current, OBJECT_VALUES[9]);
            return current;
        }

        private static long primitiveHash(int a, long b, boolean c, double d, int e, long f, boolean g, double h, int i, long j) {
            return a
                + b
                + (c ? 1 : 0)
                + Double.doubleToLongBits(d)
                + e
                + f
                + (g ? 1 : 0)
                + Double.doubleToLongBits(h)
                + i
                + j;
        }

        private static long boxedHash(Integer a, Long b, Boolean c, Double d, Integer e, Long f, Boolean g, Double h, Integer i, Long j) {
            return primitiveHash(a, b, c, d, e, f, g, h, i, j);
        }

        private static long objectHash(String a, String b, String c, String d, String e, String f, String g, String h, String i, String j) {
            return a.length()
                + b.length()
                + c.length()
                + d.length()
                + e.length()
                + f.length()
                + g.length()
                + h.length()
                + i.length()
                + j.length();
        }
    }

    public enum Access {
        DIRECT,
        METHOD_HANDLE,
        UNSAFE_INTROSPECTION
    }
}
