package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.databind.JacksonDatabindMapper;
import io.micronaut.json.JsonMapper;
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

    private static final Argument<Input> INPUT_ARGUMENT = Argument.of(Input.class);
    private static final Argument<SimpleString> SIMPLE_STRING_ARGUMENT = Argument.of(SimpleString.class);
    private static final Argument<SimpleStringArray> SIMPLE_STRING_ARRAY_ARGUMENT = Argument.of(SimpleStringArray.class);
    private static final Argument<SimpleStringList> SIMPLE_STRING_LIST_ARGUMENT = Argument.of(SimpleStringList.class);
    private static final Argument<SimpleInteger> SIMPLE_INTEGER_ARGUMENT = Argument.of(SimpleInteger.class);
    private static final Argument<SimpleInt> SIMPLE_INT_ARGUMENT = Argument.of(SimpleInt.class);
    private static final Argument<SimpleIntegerArray> SIMPLE_INTEGER_ARRAY_ARGUMENT = Argument.of(SimpleIntegerArray.class);

    @Benchmark
    public Object simpleInput(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"haystack\": [\"xniomb\", \"seelzp\", \"nzogdq\", \"omblsg\", \"idgtlm\", \"ydonzo\"], \"needle\": \"idg\"}",
            INPUT_ARGUMENT
        );
    }

    @Benchmark
    public Object testSimpleString(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"str\":\"myString\"}",
            SIMPLE_STRING_ARGUMENT
        );
    }

    @Benchmark
    public Object testSimpleStringArray(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"strs\":[\"myString1\",\"myString2\"]}",
            SIMPLE_STRING_ARRAY_ARGUMENT
        );
    }

    @Benchmark
    public Object testSimpleStringList(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"strs\":[\"myString1\",\"myString2\"]}",
            SIMPLE_STRING_LIST_ARGUMENT
        );
    }

    @Benchmark
    public Object testSimpleInteger(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"integer\":123}",
            SIMPLE_INTEGER_ARGUMENT
        );
    }

    @Benchmark
    public Object testSimpleIntegerArray(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"integers\":[123, 456]}",
            SIMPLE_INTEGER_ARRAY_ARGUMENT
        );
    }

    @Benchmark
    public Object testSimpleInt(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"integer\":123}",
            SIMPLE_INT_ARGUMENT
        );
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(JacksonBenchmark.class.getName() + ".*")
            .warmupIterations(10)
            .measurementIterations(15)
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
            INPUT_ARGUMENT
        );
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param({
            "JACKSON_DATABIND",
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
            } else if (stack == Stack.JACKSON_DATABIND) {
                jsonMapper = ctx.getBean(JacksonDatabindMapper.class);
            }
        }

        @TearDown
        public void tearDown() {
            ctx.close();
        }
    }

    public enum Stack {
        SERDE_JACKSON,
        JACKSON_DATABIND
    }

}
