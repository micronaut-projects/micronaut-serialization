package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.bson.BsonBinaryMapper;
import io.micronaut.serde.cbor.CborObjectMapper;
import io.micronaut.serde.data.Users;
import io.micronaut.serde.jackson.JacksonJsonMapper;
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
import java.util.Map;

/**
 * Compares Micronaut Serde format runtimes on the same {@link Users} payload:
 * JSON ({@link JacksonJsonMapper}), BSON binary ({@link BsonBinaryMapper}), and CBOR
 * ({@link CborObjectMapper}).
 *
 * <p>Run:
 * {@code ./gradlew :micronaut-benchmarks:jmh -Pjmh.includes='.*FormatComparisonBenchmark.*'
 * -Pjmh.forks=3 -Pjmh.warmupIterations=5 -Pjmh.iterations=10 -Pjmh.warmup=1s -Pjmh.timeOnIteration=1s}</p>
 */
public class FormatComparisonBenchmark {

    private static final String SERDE_JSON = "Serde JSON";
    private static final String SERDE_BSON_BINARY = "Serde BSON Binary";
    private static final String SERDE_CBOR = "Serde CBOR";

    private static final Argument<Users> USERS_ARGUMENT = Argument.of(Users.class);
    private static final byte[] USERS_JSON = """
        {"users":[{"_id":"39771757156730064829","index":1031703887,"guid":"ifhsrU6geU4PijjDE8Q5","isActive":false,"balance":"TKl0GcwTs72S4CPx5rfg","picture":"FkKrg6ZOPC5REchlhixu5WgIl3gNAqq28iLtFm6dKfTSQs8d3P0cYxKsEvbvMB2C6BVgExop3khRlNSFE4SV8dVFitFs7RyyecN8","age":5,"eyeColor":"AY79Pw4sYByUZEMLxnYJ","name":"XjXrEZMuTvPnuOPBg7hL","gender":"VaMcuWBHvnWvIlCC9q4T","company":"6pmCe1LxouRGfZD79ena","email":"TboNtpmAS0ppZ07jITFE","phone":"j8OoUhtmwBlI20EgD1LS","address":"Aqo4fSYBpvvAWTDqbFbK","about":"1kXFSA2782BLqNBbKIbp","registered":"Mc7h3gZJcQ11ShGQYdXI","latitude":13.474549605725421,"longitude":35.010833129741435,"tags":["8tGfPhZkZD","XYmwuAAtZ4","u9iBDMpS9G","4udy1eRqme","Lg48Ogrf0I","zku019kVpo","iuIMkiZzog","MuI1uYeCjc","49n7qisFD8","TtVgWerCRh","H604QRJmi1","ZIQMfqInNH","CbDyjjA19F","pNFwPdkVdU","aPFLsUbIUh","fA735PT0Hd","00etYDYL87","mlyEf1lI2B","RQ05IJSzXF","3jJt0Zrkhw","ZINP8GH4Bm","XebX8UvviN","EXqZ9G0ATB","ssyzWZVAa2"],"friends":[{"id":"2668","name":"lcxeDXPbnoIxAPqTNdkwbcGIJxLnPe"},{"id":"9395","name":"dxNBbezfkbotyCmFzjodONShlGFaAg"},{"id":"5249","name":"fYHSDXScMSzQvxzFuuPHYWfyjdGQLg"},{"id":"4978","name":"qfoxPWmoWUyUduVkRwhzyBusuflrFY"},{"id":"9710","name":"vUAJwshFGLoBHfwLcsEVNLJLwdaCAg"},{"id":"7404","name":"BhVMdvhPRdpwpDWAmfhNDikncdNgGr"},{"id":"1343","name":"ZeDoizPcOBafZtVYDOmpzGoHekfoxf"},{"id":"7382","name":"KtqXeVdCQJlwSNHkgkxuoIGdOWrmqG"},{"id":"1365","name":"rCSTlgbmTAFhbSfPmnftcDLwdiKsHt"},{"id":"8037","name":"PUvwVYoSvSTnwjJCQITTcwNvMOpxie"},{"id":"4858","name":"cUfQfDIiyMfCMYBKGwhZSWnRRKwlxG"},{"id":"9141","name":"rJxMGOWRjdkphthcaKTspFrMcvcLLb"},{"id":"9128","name":"gcsYaolAQqrNMQTluIAKOkwYTWVUXe"},{"id":"2268","name":"jwXOUcXAiLurRlgTdxyKWvsbNHfFxl"},{"id":"5447","name":"whivfJXOdxoHtLIGpytTdbOXxlZpUY"},{"id":"7551","name":"whykuIjZUgvOFGpmNHjoPeTeYCPNby"},{"id":"719","name":"SmbiwQaORLdsbAlUZbQwgCKfuoPLVr"},{"id":"7773","name":"LZmRMXmXXHzlzFFJAopDNnWkuBqndD"},{"id":"9602","name":"xCNsDBFMygEwZuecJKTUrqeDLBJlrR"},{"id":"1536","name":"hrfeFnKnmVgZDDOxAHgXfgcJSRyiXB"},{"id":"3549","name":"NvvhXwWgCSaYijqhxsrxIWrHbBOOIa"}],"greeting":"hTAIJLspvLr8DJPG3jYh","favoriteFruit":"f6ZsZ3saRGKMBCZLAkiP"}]}
        """.getBytes(StandardCharsets.UTF_8);

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void serialize(Holder holder, Blackhole blackhole) throws IOException {
        blackhole.consume(holder.mapper.writeValueAsBytes(USERS_ARGUMENT, holder.users));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void deserialize(Holder holder, Blackhole blackhole) throws IOException {
        blackhole.consume(holder.mapper.readValue(holder.encoded, USERS_ARGUMENT));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void roundTrip(Holder holder, Blackhole blackhole) throws IOException {
        byte[] bytes = holder.mapper.writeValueAsBytes(USERS_ARGUMENT, holder.users);
        blackhole.consume(bytes);
        blackhole.consume(holder.mapper.readValue(bytes, USERS_ARGUMENT));
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param({
            SERDE_JSON,
            SERDE_BSON_BINARY,
            SERDE_CBOR
        })
        String stack = SERDE_JSON;

        ApplicationContext context;
        JsonMapper mapper;
        Users users;
        byte[] encoded;
        int encodedSize;

        @Setup
        public void setUp() throws Exception {
            context = ApplicationContext.run(Map.of(
                "micronaut.serde.serialization.inclusion", "ALWAYS",
                // CBOR prefers native byte strings; harmless for JSON/BSON paths used here
                "micronaut.serde.cbor.write-binary-as-array", "false"
            ));

            JsonMapper bootstrapJson = context.getBean(JacksonJsonMapper.class).createSpecific(USERS_ARGUMENT);
            users = bootstrapJson.readValue(USERS_JSON, USERS_ARGUMENT);

            if (SERDE_JSON.equals(stack)) {
                mapper = bootstrapJson;
            } else if (SERDE_BSON_BINARY.equals(stack)) {
                mapper = context.getBean(BsonBinaryMapper.class).createSpecific(USERS_ARGUMENT);
            } else if (SERDE_CBOR.equals(stack)) {
                mapper = context.getBean(CborObjectMapper.class).createSpecific(USERS_ARGUMENT);
            } else {
                throw new IllegalStateException("Unsupported stack: " + stack);
            }

            encoded = mapper.writeValueAsBytes(USERS_ARGUMENT, users);
            encodedSize = encoded.length;

            // Round-trip sanity so a broken stack fails setup instead of silently skewing results
            Users reread = mapper.readValue(encoded, USERS_ARGUMENT);
            if (reread == null || reread.users == null || reread.users.isEmpty()) {
                throw new IllegalStateException("Format stack failed round-trip: " + stack);
            }

            System.out.printf("FormatComparisonBenchmark setup: stack=%s encodedSize=%d bytes%n", stack, encodedSize);
        }

        @TearDown
        public void tearDown() {
            if (context != null) {
                context.close();
            }
        }
    }
}
