package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.databind.JacksonDatabindMapper;
import io.micronaut.serde.data.SimpleBean;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

public class StartupBenchmark {

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public Object fullStartupAndGetMapper(Holder1 holder) {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            if (holder.stack == Stack.SERDE_JACKSON) {
                return ctx.getBean(JacksonJsonMapper.class);
            } else if (holder.stack == Stack.JACKSON_DATABIND) {
                return ctx.getBean(JacksonDatabindMapper.class);
            }
            throw new IllegalStateException();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public Object fullStartupAndGetMapperReadBean(Holder1 holder) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            if (holder.stack == Stack.SERDE_JACKSON) {
                return ctx.getBean(JacksonJsonMapper.class).readValue("{}", Argument.of(SimpleBean.class));
            } else if (holder.stack == Stack.JACKSON_DATABIND) {
                return ctx.getBean(JacksonDatabindMapper.class).readValue("{}", Argument.of(SimpleBean.class));
            }
            throw new IllegalStateException();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public Object getMapper(Holder2 holder) {
        if (holder.stack == Stack.SERDE_JACKSON) {
            return holder.ctx.getBean(JacksonJsonMapper.class);
        } else if (holder.stack == Stack.JACKSON_DATABIND) {
            return holder.ctx.getBean(JacksonDatabindMapper.class);
        }
        throw new IllegalStateException();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public Object getMapperReadBean(Holder2 holder) throws Exception {
        if (holder.stack == Stack.SERDE_JACKSON) {
            return holder.ctx.getBean(JacksonJsonMapper.class).readValue("{}", Argument.of(SimpleBean.class));
        } else if (holder.stack == Stack.JACKSON_DATABIND) {
            return holder.ctx.getBean(JacksonDatabindMapper.class).readValue("{}", Argument.of(SimpleBean.class));
        }
        throw new IllegalStateException();
    }

    public static void main(String[] args) {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            ctx.getBean(JacksonJsonMapper.class);
        }
    }


    @State(Scope.Thread)
    public static class Holder1 {
        @Param({
            "SERDE_JACKSON",
            "JACKSON_DATABIND"

        })
        Stack stack = Stack.SERDE_JACKSON;

    }

    @State(Scope.Thread)
    public static class Holder2 {
        @Param({
            "SERDE_JACKSON",
            "JACKSON_DATABIND"
        })
        Stack stack = Stack.SERDE_JACKSON;

        ApplicationContext ctx;

        @Setup
        public void setUp() {
            ctx = ApplicationContext.run();
        }

        @TearDown
        public void tearDown() {
            ctx.close();
        }
    }

    public enum Stack {
        SERDE_JACKSON,
        JACKSON_DATABIND,
    }

}
