package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.data.StringArrayField;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;

public class ComboBenchmark {

    @State(Scope.Thread)
    public static class Holder {
        @Param
        TestCase testCase;

        JsonMapper jsonMapper;
        ApplicationContext ctx;

        @Setup
        public void setUp() {
            ctx = ApplicationContext.run();
            jsonMapper = ctx.getBean(JacksonJsonMapper.class).createSpecific(testCase.type);
        }

        @TearDown
        public void tearDown() {
            ctx.close();
        }
    }

    public enum TestCase {
        STRING_ARRAY_FIELD(Argument.of(StringArrayField.class), new StringArrayField() {{ strs = new String[] { "foo", "bar" }; }}),
        STRING(Argument.of(String.class), "foo");

        final Argument<?> type;
        final Object input;

        TestCase(Argument<?> type, Object input) {
            this.type = type;
            this.input = input;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Benchmark
    public Object serde(Holder holder, Blackhole bh) throws IOException {
        byte[] bytes = holder.jsonMapper.writeValueAsBytes((Argument) holder.testCase.type, holder.testCase.input);
        bh.consume(bytes);
        return holder.jsonMapper.readValue(bytes, holder.testCase.type);
    }
}
