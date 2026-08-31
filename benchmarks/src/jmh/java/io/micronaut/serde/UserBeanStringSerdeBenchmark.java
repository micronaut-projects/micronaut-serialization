package io.micronaut.serde;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.data.Users;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Compares the String-based mapper API ({@code writeValueAsString} / {@code readValue(String)})
 * against the equivalent byte-array round trip it previously delegated to. Not part of the
 * default benchmark task; run explicitly with {@code -Pjmh.includes=UserBeanStringSerdeBenchmark}.
 */
public class UserBeanStringSerdeBenchmark {

    private static final Argument<Users> USERS_ARGUMENT = Argument.of(Users.class);
    private static final String USERS_JSON_STRING = UserBeanSerdeBenchmark.usersJsonString();

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public String serializeString(UserBeanSerdeBenchmark.Holder holder) throws IOException {
        return holder.jsonMapper.writeValueAsString(USERS_ARGUMENT, holder.users);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public String serializeStringViaBytes(UserBeanSerdeBenchmark.Holder holder) throws IOException {
        return new String(holder.jsonMapper.writeValueAsBytes(USERS_ARGUMENT, holder.users), StandardCharsets.UTF_8);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object deserializeString(UserBeanSerdeBenchmark.Holder holder) throws IOException {
        return holder.jsonMapper.readValue(USERS_JSON_STRING, USERS_ARGUMENT);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object deserializeStringViaBytes(UserBeanSerdeBenchmark.Holder holder) throws IOException {
        return holder.jsonMapper.readValue(USERS_JSON_STRING.getBytes(StandardCharsets.UTF_8), USERS_ARGUMENT);
    }
}
