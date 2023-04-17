package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.databind.JacksonDatabindMapper;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.data.InputConstructor;
import io.micronaut.serde.data.InputField;
import io.micronaut.serde.data.InputSetter;
import io.micronaut.serde.data.IntArrayConstructor;
import io.micronaut.serde.data.IntArrayField;
import io.micronaut.serde.data.IntConstructor;
import io.micronaut.serde.data.IntegerConstructor;
import io.micronaut.serde.data.IntegerField;
import io.micronaut.serde.data.StringArrayConstructor;
import io.micronaut.serde.data.StringArrayField;
import io.micronaut.serde.data.StringConstructor;
import io.micronaut.serde.data.StringField;
import io.micronaut.serde.data.StringListConstructor;
import io.micronaut.serde.data.StringListField;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class JacksonBenchmark {

    private static final Argument<InputConstructor> INPUT_CONSTRUCTOR_ARGUMENT = Argument.of(InputConstructor.class);
    private static final Argument<InputField> INPUT_FIELD_ARGUMENT = Argument.of(InputField.class);
    private static final Argument<InputSetter> INPUT_SETTER_ARGUMENT = Argument.of(InputSetter.class);

    private static final Argument<StringConstructor> STRING_CONSTRUCTOR_ARGUMENT = Argument.of(StringConstructor.class);
    private static final Argument<StringField> STRING_FIELD_ARGUMENT = Argument.of(StringField.class);

    private static final Argument<StringArrayConstructor> STRING_ARRAY_CONSTRUCTOR_ARGUMENT = Argument.of(StringArrayConstructor.class);
    private static final Argument<StringArrayField> STRING_ARRAY_FIELD_ARGUMENT = Argument.of(StringArrayField.class);

    private static final Argument<StringListConstructor> STRING_LIST_CONSTRUCTOR_ARGUMENT = Argument.of(StringListConstructor.class);
    private static final Argument<StringListField> STRING_LIST_FIELD_ARGUMENT = Argument.of(StringListField.class);

    private static final Argument<IntegerConstructor> INTEGER_CONSTRUCTOR_ARGUMENT = Argument.of(IntegerConstructor.class);
    private static final Argument<IntegerField> INTEGER_FIELD_ARGUMENT = Argument.of(IntegerField.class);

    private static final Argument<IntConstructor> INT_CONSTRUCTOR_ARGUMENT = Argument.of(IntConstructor.class);
    private static final Argument<IntegerField> INT_FIELD_ARGUMENT = Argument.of(IntegerField.class);

    private static final Argument<IntArrayConstructor> INTEGER_ARRAY_CONSTRUCTOR_ARGUMENT = Argument.of(IntArrayConstructor.class);
    private static final Argument<IntArrayField> INTEGER_ARRAY_FIELD_ARGUMENT = Argument.of(IntArrayField.class);

    @Benchmark
    public Object decodeInputConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"haystack\": [\"xniomb\", \"seelzp\", \"nzogdq\", \"omblsg\", \"idgtlm\", \"ydonzo\"], \"needle\": \"idg\"}",
            INPUT_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeInputField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"haystack\": [\"xniomb\", \"seelzp\", \"nzogdq\", \"omblsg\", \"idgtlm\", \"ydonzo\"], \"needle\": \"idg\"}",
            INPUT_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeInputSetter(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"haystack\": [\"xniomb\", \"seelzp\", \"nzogdq\", \"omblsg\", \"idgtlm\", \"ydonzo\"], \"needle\": \"idg\"}",
            INPUT_SETTER_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"str\":\"myString\"}",
            STRING_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"str\":\"myString\"}",
            STRING_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringArrayConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"strs\":[\"myString1\",\"myString2\"]}",
            STRING_ARRAY_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringArrayField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"strs\":[\"myString1\",\"myString2\"]}",
            STRING_ARRAY_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringListConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"strs\":[\"myString1\",\"myString2\"]}",
            STRING_LIST_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringListField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"strs\":[\"myString1\",\"myString2\"]}",
            STRING_LIST_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeIntegerConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"integer\":123}",
            INTEGER_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeIntegerField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"integer\":123}",
            INTEGER_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeIntegerArrayConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"integers\":[123, 456]}",
            INTEGER_ARRAY_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeIntegerArrayField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"integers\":[123, 456]}",
            INTEGER_ARRAY_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeIntConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"integer\":123}",
            INT_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeIntField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"integer\":123}",
            INT_FIELD_ARGUMENT
        );
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(JacksonBenchmark.class.getName() + ".*")
            .warmupIterations(5)
            .measurementIterations(10)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
//            .addProfiler(AsyncProfiler.class, "libPath=/Users/denisstepanov/dev/async-profiler-2.9-macos/build/libasyncProfiler.dylib;output=flamegraph")
//            .addProfiler(AsyncProfiler.class, "libPath=/Users/denisstepanov/dev/async-profiler-2.9-macos/build/libasyncProfiler.dylib;output=flamegraph")
//            .addProfiler(AsyncProfiler.class, "libPath=/home/yawkat/bin/async-profiler-2.9-linux-x64/build/libasyncProfiler.so;output=flamegraph")
            .forks(1)
//            .jvmArgsPrepend("-Dio.type.pollution.file=out.txt", "-javaagent:/Users/denisstepanov/dev/micronaut-core/type-pollution-agent-0.1-SNAPSHOT.jar")
            .build();

        new Runner(opt).run();
    }

    public static void mainx(String[] args) throws Exception {
        ApplicationContext ctx = ApplicationContext.run();

        JsonMapper jsonMapper = ctx.getBean(JacksonJsonMapper.class);
        jsonMapper.readValue(
            "{\"haystack\": [\"xniomb\", \"seelzp\", \"nzogdq\", \"omblsg\", \"idgtlm\", \"ydonzo\"], \"needle\": \"idg\"}",
            INPUT_FIELD_ARGUMENT
        );
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param({
            "JACKSON_DATABIND_INTROSPECTION",
            "JACKSON_DATABIND_REFLECTION",
            "SERDE_JACKSON"
        })
        Stack stack = Stack.SERDE_JACKSON;

        JsonMapper jsonMapper;
        ApplicationContext ctx;

        @Setup
        public void setUp() {
            ctx = ApplicationContext.run();

            if (stack == Stack.SERDE_JACKSON) {
                jsonMapper = ctx.getBean(JacksonJsonMapper.class);
            } else if (stack == Stack.JACKSON_DATABIND_INTROSPECTION) {
                jsonMapper = ctx.getBean(JacksonDatabindMapper.class);
            } else if (stack == Stack.JACKSON_DATABIND_REFLECTION) {
                jsonMapper = new JacksonDatabindMapper();
            }
        }

        @TearDown
        public void tearDown() {
            ctx.close();
        }
    }

    public enum Stack {
        SERDE_JACKSON,
        JACKSON_DATABIND_INTROSPECTION,
        JACKSON_DATABIND_REFLECTION
    }

}
