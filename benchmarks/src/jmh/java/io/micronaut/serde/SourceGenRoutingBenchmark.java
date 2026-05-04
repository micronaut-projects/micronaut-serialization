package io.micronaut.serde;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.annotation.SerdeableGenerated;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SourceGenRoutingBenchmark {

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void serializeGenerated(Holder holder, Blackhole blackhole) throws IOException {
        blackhole.consume(holder.mapper.writeValueAsBytes(holder.generatedType, holder.generatedSample));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void serializeIntrospectionFallback(Holder holder, Blackhole blackhole) throws IOException {
        blackhole.consume(holder.mapper.writeValueAsBytes(holder.fallbackType, holder.fallbackSample));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object deserializeGenerated(Holder holder) throws IOException {
        return holder.mapper.readValue(holder.generatedPayload, holder.generatedType);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object deserializeIntrospectionFallback(Holder holder) throws IOException {
        return holder.mapper.readValue(holder.fallbackPayload, holder.fallbackType);
    }

    @State(Scope.Thread)
    public static class Holder {
        ApplicationContext context;
        JsonMapper mapper;
        Argument<GeneratedShape> generatedType;
        Argument<IntrospectionFallbackShape> fallbackType;
        GeneratedShape generatedSample;
        IntrospectionFallbackShape fallbackSample;
        byte[] generatedPayload;
        byte[] fallbackPayload;

        @Setup
        public void setUp() throws IOException {
            context = ApplicationContext.run();
            mapper = context.getBean(JacksonJsonMapper.class);
            generatedType = Argument.of(GeneratedShape.class);
            fallbackType = Argument.of(IntrospectionFallbackShape.class);

            generatedSample = new GeneratedShape("generated", 42);
            fallbackSample = new IntrospectionFallbackShape("fallback", Map.of("extra", 42));

            generatedPayload = mapper.writeValueAsBytes(generatedType, generatedSample);
            fallbackPayload = mapper.writeValueAsBytes(fallbackType, fallbackSample);
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @SerdeableGenerated
    @Introspected
    public record GeneratedShape(String name, int count) {
    }

    @Serdeable
    @Introspected
    public static final class IntrospectionFallbackShape {
        private String name;
        private Map<String, Integer> attributes = new LinkedHashMap<>();

        public IntrospectionFallbackShape() {
        }

        public IntrospectionFallbackShape(String name, Map<String, Integer> attributes) {
            this.name = name;
            this.attributes = attributes;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonAnyGetter
        public Map<String, Integer> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, Integer> attributes) {
            this.attributes = attributes;
        }
    }
}
