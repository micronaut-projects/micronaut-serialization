package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.csv.CsvObjectMapper;
import io.micronaut.serde.csv.apache.commons.ApacheCommonsCsvObjectMapper;
import io.micronaut.serde.csv.jackson.JacksonCsvObjectMapper;
import io.micronaut.serde.data.FlatUser;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.Map;

/**
 * Compares Jackson CSV and Apache Commons CSV backends on the same flat {@link FlatUser}
 * payload so throughput numbers are apples-to-apples.
 *
 * <p>Run:
 * {@code ./gradlew :micronaut-benchmarks:jmh -Pjmh.includes='.*CsvComparisonBenchmark.*'
 * -Pjmh.forks=3 -Pjmh.warmupIterations=5 -Pjmh.iterations=10 -Pjmh.warmup=1s -Pjmh.timeOnIteration=1s}
 */
public class CsvComparisonBenchmark {

    private static final String SERDE_JACKSON_CSV = "Serde CSV (Jackson)";
    private static final String SERDE_COMMONS_CSV = "Serde CSV (Commons)";

    private static final Argument<FlatUser> USER_ARGUMENT = Argument.of(FlatUser.class);
    private static final FlatUser USER = FlatUser.sample();

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void serialize(Holder holder, Blackhole blackhole) throws IOException {
        blackhole.consume(holder.writeOne());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void deserialize(Holder holder, Blackhole blackhole) throws IOException {
        blackhole.consume(holder.readOne());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void roundTrip(Holder holder, Blackhole blackhole) throws IOException {
        byte[] bytes = holder.writeOne();
        blackhole.consume(bytes);
        blackhole.consume(holder.readOneFrom(bytes));
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param({SERDE_JACKSON_CSV, SERDE_COMMONS_CSV})
        String stack;

        ApplicationContext context;
        CsvObjectMapper csvMapper;
        FlatUser object = USER;
        byte[] encoded;

        FlatUser readOne() throws IOException {
            return csvMapper.readValue(encoded, USER_ARGUMENT);
        }

        FlatUser readOneFrom(byte[] bytes) throws IOException {
            return csvMapper.readValue(bytes, USER_ARGUMENT);
        }

        byte[] writeOne() throws IOException {
            return csvMapper.writeValueAsBytes(object);
        }

        @Setup
        public void setUp() throws Exception {
            context = ApplicationContext.run(Map.of("micronaut.serde.serialization.inclusion", "ALWAYS"));
            if (stack.equals(SERDE_JACKSON_CSV)) {
                csvMapper = context.getBean(JacksonCsvObjectMapper.class);
            } else if (stack.equals(SERDE_COMMONS_CSV)) {
                csvMapper = context.getBean(ApacheCommonsCsvObjectMapper.class);
            }

            encoded = writeOne();
            FlatUser reread = readOneFrom(encoded);
            if (reread == null || reread.id == null || !reread.id.equals(USER.id)) {
                throw new IllegalStateException("Format stack failed round-trip: " + stack);
            }

            System.out.printf("CsvComparisonBenchmark setup: stack=%s encodedSize=%d bytes%n",
                    stack, encoded.length);
        }

        @TearDown
        public void tearDown() {
            if (context != null) {
                context.close();
            }
        }
    }
}
