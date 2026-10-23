package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.data.Users;
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
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Reads and writes one document per call with the shared mapper, like an HTTP request body, so the specific
 * serdes are created for every document.
 */
@State(Scope.Benchmark)
public class PerDocumentSerdeBenchmark {

    private static final Argument<Users> USERS_ARGUMENT = Argument.of(Users.class);
    private static final byte[] USERS_JSON = UserBeanSerdeBenchmark.usersJsonString().getBytes(StandardCharsets.UTF_8);

    ApplicationContext context;
    JsonMapper jsonMapper;
    Users users;

    @Setup
    public void setUp() throws Exception {
        context = ApplicationContext.run(Map.of("micronaut.serde.serialization.inclusion", "ALWAYS"));
        jsonMapper = context.getBean(JacksonJsonMapper.class);
        users = jsonMapper.readValue(USERS_JSON, USERS_ARGUMENT);
        SerdeRegistry registry = context.getBean(SerdeRegistry.class);
        String generated = registry.findDeserializer(USERS_ARGUMENT)
            .createSpecific(registry.newDecoderContext(null), USERS_ARGUMENT).getClass().getName();
        if (!generated.equals("io.micronaut.serde.data.SerdeUsersDeserializer")) {
            throw new IllegalStateException("Expected the generated deserializer, got " + generated);
        }
    }

    @TearDown
    public void tearDown() {
        context.close();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public Object deserialize() throws IOException {
        return jsonMapper.readValue(USERS_JSON, USERS_ARGUMENT);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void serialize(Blackhole blackhole) throws IOException {
        blackhole.consume(jsonMapper.writeValueAsBytes(USERS_ARGUMENT, users));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public Object roundTrip() throws IOException {
        byte[] bytes = jsonMapper.writeValueAsBytes(USERS_ARGUMENT, users);
        return jsonMapper.readValue(bytes, USERS_ARGUMENT);
    }
}
