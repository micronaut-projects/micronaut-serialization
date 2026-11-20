package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.databind.JacksonDatabindMapper;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.fory.json.ForyJsonMapper;
import io.micronaut.serde.data.Users;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import org.apache.fory.json.ForyJson;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * Spike: compares Serde over Fory's reader/writer with Serde over Jackson core, plus Fory's own
 * generated codecs as a ceiling.
 */
public class ForyUserBeanBenchmark {

    private static final String JACKSON_DATABIND = "Jackson Databind";
    private static final String SERDE_JACKSON_GENERATED = "Serde Jackson Generated";
    private static final String SERDE_JACKSON_RUNTIME = "Serde Jackson Runtime";
    private static final String SERDE_FORY_GENERATED = "Serde Fory Generated";
    private static final String SERDE_FORY_RUNTIME = "Serde Fory Runtime";
    private static final String FORY_JSON_NATIVE = "Fory JSON Native";
    private static final Argument<Users> USERS_ARGUMENT = Argument.of(Users.class);

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void serialize(Holder holder, Blackhole blackhole) throws IOException {
        blackhole.consume(holder.codec.write(holder.users));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object deserialize(Holder holder) throws IOException {
        return holder.codec.read(holder.json);
    }

    interface Codec {
        byte[] write(Users users) throws IOException;

        Users read(byte[] json) throws IOException;
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param({
            JACKSON_DATABIND,
            SERDE_JACKSON_GENERATED,
            SERDE_JACKSON_RUNTIME,
            SERDE_FORY_GENERATED,
            SERDE_FORY_RUNTIME,
            FORY_JSON_NATIVE
        })
        String stack = SERDE_FORY_GENERATED;

        Codec codec;
        ApplicationContext context;
        Users users;
        byte[] json;

        @Setup
        public void setUp() throws Exception {
            context = ApplicationContext.run(Map.of(
                "micronaut.serde.serialization.inclusion", "ALWAYS"
            ));
            JsonMapper reference = context.getBean(JacksonJsonMapper.class).createSpecific(USERS_ARGUMENT);
            json = UserBeanSerdeBenchmark.USERS_JSON;
            users = reference.readValue(json, USERS_ARGUMENT);
            byte[] expected = reference.writeValueAsBytes(USERS_ARGUMENT, users);

            codec = switch (stack) {
                case JACKSON_DATABIND -> mapperCodec(new JacksonDatabindMapper(
                    tools.jackson.databind.json.JsonMapper.builder().build()).createSpecific(USERS_ARGUMENT));
                case SERDE_JACKSON_GENERATED -> mapperCodec(reference);
                case SERDE_JACKSON_RUNTIME -> mapperCodec(runtimeContext().getBean(JacksonJsonMapper.class).createSpecific(USERS_ARGUMENT));
                case SERDE_FORY_GENERATED -> mapperCodec(context.getBean(ForyJsonMapper.class).createSpecific(USERS_ARGUMENT));
                case SERDE_FORY_RUNTIME -> mapperCodec(runtimeContext().getBean(ForyJsonMapper.class).createSpecific(USERS_ARGUMENT));
                case FORY_JSON_NATIVE -> {
                    ForyJson fory = ForyJson.builder().build();
                    yield new Codec() {
                        @Override
                        public byte[] write(Users value) {
                            return fory.toJsonBytes(value, Users.class);
                        }

                        @Override
                        public Users read(byte[] bytes) {
                            return fory.fromJson(bytes, Users.class);
                        }
                    };
                }
                default -> throw new IllegalStateException("Unsupported stack: " + stack);
            };

            // Correctness: decode with the stack, re-encode with the reference mapper; and decode the
            // stack's own output with the reference mapper. Both must reproduce the reference bytes.
            byte[] viaRead = reference.writeValueAsBytes(USERS_ARGUMENT, codec.read(json));
            byte[] viaWrite = reference.writeValueAsBytes(USERS_ARGUMENT, reference.readValue(codec.write(users), USERS_ARGUMENT));
            if (!Arrays.equals(expected, viaRead) || !Arrays.equals(expected, viaWrite)) {
                throw new IllegalStateException(stack + " output mismatch:\nexpected " + new String(expected, StandardCharsets.UTF_8)
                    + "\nread     " + new String(viaRead, StandardCharsets.UTF_8)
                    + "\nwrite    " + new String(codec.write(users), StandardCharsets.UTF_8));
            }
        }

        private ApplicationContext runtimeContext() {
            context.close();
            context = ApplicationContext.run(Map.of(
                "micronaut.serde.serialization.inclusion", "ALWAYS",
                "micronaut.serde.serialization.disable-generated-serializer", true,
                "micronaut.serde.deserialization.disable-generated-deserializer", true
            ));
            return context;
        }

        private static Codec mapperCodec(JsonMapper mapper) {
            return new Codec() {
                @Override
                public byte[] write(Users value) throws IOException {
                    return mapper.writeValueAsBytes(USERS_ARGUMENT, value);
                }

                @Override
                public Users read(byte[] bytes) throws IOException {
                    return mapper.readValue(bytes, USERS_ARGUMENT);
                }
            };
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }
}
