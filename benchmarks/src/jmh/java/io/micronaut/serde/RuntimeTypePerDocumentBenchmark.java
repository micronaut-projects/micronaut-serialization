package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.data.zoo.Animal;
import io.micronaut.serde.data.zoo.Cat;
import io.micronaut.serde.data.zoo.Dog;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Documents whose values are written by their runtime type: a list of subtyped beans and a nested
 * {@code Map<String, Object>}, each written through a mapper call that creates its serializer for the document.
 */
@State(Scope.Benchmark)
public class RuntimeTypePerDocumentBenchmark {

    private static final Argument<List<Animal>> ANIMALS = Argument.listOf(Animal.class);
    private static final Argument<Map<String, Object>> MAP = Argument.mapOf(String.class, Object.class);

    @Param({"generated", "runtime"})
    String serdes;

    ApplicationContext context;
    JsonMapper jsonMapper;
    List<Animal> animals;
    Map<String, Object> map;

    @Setup
    public void setUp() {
        context = ApplicationContext.run("runtime".equals(serdes) ? Map.of(
            "micronaut.serde.serialization.disable-generated-serializer", true,
            "micronaut.serde.deserialization.disable-generated-deserializer", true
        ) : Map.of());
        jsonMapper = context.getBean(JacksonJsonMapper.class);
        animals = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            if (i % 2 == 0) {
                Dog dog = new Dog();
                dog.setName("dog" + i);
                dog.setBarkVolume(i);
                animals.add(dog);
            } else {
                Cat cat = new Cat();
                cat.setName("cat" + i);
                cat.setLives(i);
                animals.add(cat);
            }
        }
        map = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> level2 = new LinkedHashMap<>();
            level2.put("name", "item" + i);
            level2.put("count", i);
            List<Object> level3 = new ArrayList<>();
            for (int j = 0; j < 3; j++) {
                level3.add(Map.of("key", "value" + j, "flag", j % 2 == 0));
            }
            level2.put("children", level3);
            map.put("entry" + i, level2);
        }
    }

    @TearDown
    public void tearDown() {
        context.close();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void serializePolymorphicList(Blackhole blackhole) throws IOException {
        blackhole.consume(jsonMapper.writeValueAsBytes(ANIMALS, animals));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void serializeNestedMap(Blackhole blackhole) throws IOException {
        blackhole.consume(jsonMapper.writeValueAsBytes(MAP, map));
    }
}
