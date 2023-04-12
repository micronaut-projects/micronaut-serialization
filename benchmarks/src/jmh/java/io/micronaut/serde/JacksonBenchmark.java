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

    @Benchmark
    public Object test(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            "{\"haystack\": [\"xniomb\", \"seelzp\", \"nzogdq\", \"omblsg\", \"idgtlm\", \"ydonzo\"], \"needle\": \"idg\"}",
            INPUT_ARGUMENT
        );
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(JacksonBenchmark.class.getName() + ".*")
            .warmupIterations(1)
            .measurementIterations(1)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
//            .addProfiler(AsyncProfiler.class, "libPath=/Users/denisstepanov/dev/async-profiler-2.9-macos/build/libasyncProfiler.dylib;output=flamegraph")
//            .addProfiler(AsyncProfiler.class, "libPath=/home/yawkat/bin/async-profiler-2.9-linux-x64/build/libasyncProfiler.so;output=flamegraph")
            .forks(1)
//            .jvmArgsPrepend("-Dio.type.pollution.file=out.txt", "-javaagent:/Users/denisstepanov/dev/micronaut-core/type-pollution-agent-0.1-SNAPSHOT.jar")
            .build();

        new Runner(opt).run();
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param({"SERDE_JACKSON"})
        Stack stack = Stack.SERDE_JACKSON;

        JsonMapper jsonMapper;
        ApplicationContext ctx;

        @Setup
        public void setUp() {
            ApplicationContext ctx = ApplicationContext.run();

            if (stack == Stack.SERDE_JACKSON) {
                jsonMapper = ctx.getBean(JacksonJsonMapper.class);
            } else if (stack == Stack.JACKSON_DATABIND) {
                jsonMapper = ctx.getBean(JacksonDatabindMapper.class);
            }
        }

        @TearDown
        public void tearDown() throws Exception {
            ctx.close();
        }
    }

    public enum Stack {
        SERDE_JACKSON,
        JACKSON_DATABIND
    }

}
