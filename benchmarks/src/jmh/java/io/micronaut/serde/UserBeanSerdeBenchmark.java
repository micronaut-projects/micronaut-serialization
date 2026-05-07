package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.databind.JacksonDatabindMapper;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.data.Users;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import io.micronaut.serde.support.deserializers.ErrorCatchingDeserializer;
import io.micronaut.serde.support.serializers.ErrorCatchingSerializer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class UserBeanSerdeBenchmark {

    private static final Argument<Users> USERS_ARGUMENT = Argument.of(Users.class);
    private static final byte[] USERS_JSON = """
        {"users":[{"_id":"39771757156730064829","index":1031703887,"guid":"ifhsrU6geU4PijjDE8Q5","isActive":false,"balance":"TKl0GcwTs72S4CPx5rfg","picture":"FkKrg6ZOPC5REchlhixu5WgIl3gNAqq28iLtFm6dKfTSQs8d3P0cYxKsEvbvMB2C6BVgExop3khRlNSFE4SV8dVFitFs7RyyecN8","age":5,"eyeColor":"AY79Pw4sYByUZEMLxnYJ","name":"XjXrEZMuTvPnuOPBg7hL","gender":"VaMcuWBHvnWvIlCC9q4T","company":"6pmCe1LxouRGfZD79ena","email":"TboNtpmAS0ppZ07jITFE","phone":"j8OoUhtmwBlI20EgD1LS","address":"Aqo4fSYBpvvAWTDqbFbK","about":"1kXFSA2782BLqNBbKIbp","registered":"Mc7h3gZJcQ11ShGQYdXI","latitude":13.474549605725421,"longitude":35.010833129741435,"tags":["8tGfPhZkZD","XYmwuAAtZ4","u9iBDMpS9G","4udy1eRqme","Lg48Ogrf0I","zku019kVpo","iuIMkiZzog","MuI1uYeCjc","49n7qisFD8","TtVgWerCRh","H604QRJmi1","ZIQMfqInNH","CbDyjjA19F","pNFwPdkVdU","aPFLsUbIUh","fA735PT0Hd","00etYDYL87","mlyEf1lI2B","RQ05IJSzXF","3jJt0Zrkhw","ZINP8GH4Bm","XebX8UvviN","EXqZ9G0ATB","ssyzWZVAa2"],"friends":[{"id":"2668","name":"lcxeDXPbnoIxAPqTNdkwbcGIJxLnPe"},{"id":"9395","name":"dxNBbezfkbotyCmFzjodONShlGFaAg"},{"id":"5249","name":"fYHSDXScMSzQvxzFuuPHYWfyjdGQLg"},{"id":"4978","name":"qfoxPWmoWUyUduVkRwhzyBusuflrFY"},{"id":"9710","name":"vUAJwshFGLoBHfwLcsEVNLJLwdaCAg"},{"id":"7404","name":"BhVMdvhPRdpwpDWAmfhNDikncdNgGr"},{"id":"1343","name":"ZeDoizPcOBafZtVYDOmpzGoHekfoxf"},{"id":"7382","name":"KtqXeVdCQJlwSNHkgkxuoIGdOWrmqG"},{"id":"1365","name":"rCSTlgbmTAFhbSfPmnftcDLwdiKsHt"},{"id":"8037","name":"PUvwVYoSvSTnwjJCQITTcwNvMOpxie"},{"id":"4858","name":"cUfQfDIiyMfCMYBKGwhZSWnRRKwlxG"},{"id":"9141","name":"rJxMGOWRjdkphthcaKTspFrMcvcLLb"},{"id":"9128","name":"gcsYaolAQqrNMQTluIAKOkwYTWVUXe"},{"id":"2268","name":"jwXOUcXAiLurRlgTdxyKWvsbNHfFxl"},{"id":"5447","name":"whivfJXOdxoHtLIGpytTdbOXxlZpUY"},{"id":"7551","name":"whykuIjZUgvOFGpmNHjoPeTeYCPNby"},{"id":"719","name":"SmbiwQaORLdsbAlUZbQwgCKfuoPLVr"},{"id":"7773","name":"LZmRMXmXXHzlzFFJAopDNnWkuBqndD"},{"id":"9602","name":"xCNsDBFMygEwZuecJKTUrqeDLBJlrR"},{"id":"1536","name":"hrfeFnKnmVgZDDOxAHgXfgcJSRyiXB"},{"id":"3549","name":"NvvhXwWgCSaYijqhxsrxIWrHbBOOIa"}],"greeting":"hTAIJLspvLr8DJPG3jYh","favoriteFruit":"f6ZsZ3saRGKMBCZLAkiP"}]}
        """.getBytes(StandardCharsets.UTF_8);
    private static final String GENERATED_SERIALIZER = "io.micronaut.serde.data.SerdeUsersSerializer";
    private static final String GENERATED_DESERIALIZER = "io.micronaut.serde.data.SerdeUsersDeserializer";
    private static final String RUNTIME_SERIALIZER = "io.micronaut.serde.support.serializers.SimpleObjectSerializer";
    private static final String RUNTIME_DESERIALIZER = "io.micronaut.serde.support.deserializers.SimpleObjectDeserializer";

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void serialize(Holder holder, Blackhole blackhole) throws IOException {
        blackhole.consume(holder.jsonMapper.writeValueAsBytes(USERS_ARGUMENT, holder.users));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object deserialize(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(USERS_JSON, USERS_ARGUMENT);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object roundTrip(Holder holder, Blackhole blackhole) throws IOException {
        byte[] bytes = holder.jsonMapper.writeValueAsBytes(USERS_ARGUMENT, holder.users);
        blackhole.consume(bytes);
        return holder.jsonMapper.readValue(bytes, USERS_ARGUMENT);
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param({
            "JACKSON_DATABIND",
            "JACKSON_DATABIND_BLACKBIRD",
            "SERDE_JACKSON_GENERATED",
            "SERDE_JACKSON_RUNTIME"
        })
        Stack stack = Stack.SERDE_JACKSON_GENERATED;

        JsonMapper jsonMapper;
        ApplicationContext context;
        Users users;

        @Setup
        public void setUp() throws Exception {
            if (stack == Stack.JACKSON_DATABIND) {
                jsonMapper = new JacksonDatabindMapper();
            } else if (stack == Stack.JACKSON_DATABIND_BLACKBIRD) {
                // Users is a public-field model. Blackbird is registered here, but it primarily
                // optimizes method-backed bean accessors, so this shape may show limited benefit.
                ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder()
                    .addModule(new BlackbirdModule())
                    .build();
                jsonMapper = new JacksonDatabindMapper(objectMapper);
            } else if (stack == Stack.SERDE_JACKSON_GENERATED) {
                context = ApplicationContext.run(Map.of(
                    "micronaut.serde.serialization.inclusion", "ALWAYS"
                ));
                jsonMapper = context.getBean(JacksonJsonMapper.class);
                validateDefaultSerdes(context);
            } else if (stack == Stack.SERDE_JACKSON_RUNTIME) {
                context = ApplicationContext.run(Map.of(
                    "micronaut.serde.serialization.inclusion", "ALWAYS",
                    "micronaut.serde.serialization.disable-generated-serializer", true,
                    "micronaut.serde.deserialization.disable-generated-deserializer", true
                ));
                jsonMapper = context.getBean(JacksonJsonMapper.class);
                validateRuntimeSerdes(context);
            } else {
                throw new IllegalStateException("Unsupported stack: " + stack);
            }
            jsonMapper = jsonMapper.createSpecific(USERS_ARGUMENT);
            users = jsonMapper.readValue(USERS_JSON, USERS_ARGUMENT);
        }

        @TearDown
        public void tearDown() {
            if (context != null) {
                context.close();
            }
        }

        private static void validateDefaultSerdes(ApplicationContext context) throws Exception {
            var registry = context.getBean(SerdeRegistry.class);
            var serializer = registry.findSerializer(USERS_ARGUMENT)
                .createSpecific(registry.newEncoderContext(Object.class), USERS_ARGUMENT);
            var deserializer = registry.findDeserializer(USERS_ARGUMENT)
                .createSpecific(registry.newDecoderContext(Object.class), USERS_ARGUMENT);
            validateSerde("default serializer", GENERATED_SERIALIZER, unwrapSerializer(serializer));
            validateSerde("default deserializer", GENERATED_DESERIALIZER, unwrapDeserializer(deserializer));
        }

        private static void validateRuntimeSerdes(ApplicationContext context) throws Exception {
            var registry = context.getBean(SerdeRegistry.class);
            var serializer = registry.findSerializer(USERS_ARGUMENT)
                .createSpecific(registry.newEncoderContext(Object.class), USERS_ARGUMENT);
            var deserializer = registry.findDeserializer(USERS_ARGUMENT)
                .createSpecific(registry.newDecoderContext(Object.class), USERS_ARGUMENT);
            validateSerde("runtime serializer", RUNTIME_SERIALIZER, unwrapSerializer(serializer));
            validateSerde("runtime deserializer", RUNTIME_DESERIALIZER, unwrapDeserializer(deserializer));
        }

        private static Serializer<?> unwrapSerializer(Serializer<?> serializer) {
            if (serializer instanceof ErrorCatchingSerializer<?> errorCatchingSerializer) {
                return errorCatchingSerializer.getSerializer();
            }
            return serializer;
        }

        private static Deserializer<?> unwrapDeserializer(Deserializer<?> deserializer) {
            if (deserializer instanceof ErrorCatchingDeserializer<?> errorCatchingDeserializer) {
                return errorCatchingDeserializer.getDeserializer();
            }
            return deserializer;
        }

        private static void validateSerde(String role, String expectedClassName, Object serde) {
            String actualClassName = serde.getClass().getName();
            if (!actualClassName.equals(expectedClassName)) {
                throw new IllegalStateException("Expected " + role + " " + expectedClassName + " but found " + actualClassName);
            }
        }
    }

    public enum Stack {
        JACKSON_DATABIND,
        JACKSON_DATABIND_BLACKBIRD,
        SERDE_JACKSON_GENERATED,
        SERDE_JACKSON_RUNTIME
    }
}
