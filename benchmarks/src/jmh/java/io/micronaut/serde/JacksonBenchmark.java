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
import io.micronaut.serde.data.Users;
import io.micronaut.serde.data.UsersNoArrays;
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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class JacksonBenchmark {

    private static final Argument<Users> USERS_ARGUMENT = Argument.of(Users.class);
    private static final Argument<UsersNoArrays> USERS_NO_ARRAYS_ARGUMENT = Argument.of(UsersNoArrays.class);

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

    private static final byte[] HAYSTACK_6_6 = "{\"haystack\": [\"xniomb\", \"seelzp\", \"nzogdq\", \"omblsg\", \"idgtlm\", \"ydonzo\"], \"needle\": \"idg\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INPUT_USERS = """
        {"users":[{"_id":"39771757156730064829","index":1031703887,"guid":"ifhsrU6geU4PijjDE8Q5","isActive":false,"balance":"TKl0GcwTs72S4CPx5rfg","picture":"FkKrg6ZOPC5REchlhixu5WgIl3gNAqq28iLtFm6dKfTSQs8d3P0cYxKsEvbvMB2C6BVgExop3khRlNSFE4SV8dVFitFs7RyyecN8","age":5,"eyeColor":"AY79Pw4sYByUZEMLxnYJ","name":"XjXrEZMuTvPnuOPBg7hL","gender":"VaMcuWBHvnWvIlCC9q4T","company":"6pmCe1LxouRGfZD79ena","email":"TboNtpmAS0ppZ07jITFE","phone":"j8OoUhtmwBlI20EgD1LS","address":"Aqo4fSYBpvvAWTDqbFbK","about":"1kXFSA2782BLqNBbKIbp","registered":"Mc7h3gZJcQ11ShGQYdXI","latitude":13.474549605725421,"longitude":35.010833129741435,"tags":["8tGfPhZkZD","XYmwuAAtZ4","u9iBDMpS9G","4udy1eRqme","Lg48Ogrf0I","zku019kVpo","iuIMkiZzog","MuI1uYeCjc","49n7qisFD8","TtVgWerCRh","H604QRJmi1","ZIQMfqInNH","CbDyjjA19F","pNFwPdkVdU","aPFLsUbIUh","fA735PT0Hd","00etYDYL87","mlyEf1lI2B","RQ05IJSzXF","3jJt0Zrkhw","ZINP8GH4Bm","XebX8UvviN","EXqZ9G0ATB","ssyzWZVAa2"],"friends":[{"id":"2668","name":"lcxeDXPbnoIxAPqTNdkwbcGIJxLnPe"},{"id":"9395","name":"dxNBbezfkbotyCmFzjodONShlGFaAg"},{"id":"5249","name":"fYHSDXScMSzQvxzFuuPHYWfyjdGQLg"},{"id":"4978","name":"qfoxPWmoWUyUduVkRwhzyBusuflrFY"},{"id":"9710","name":"vUAJwshFGLoBHfwLcsEVNLJLwdaCAg"},{"id":"7404","name":"BhVMdvhPRdpwpDWAmfhNDikncdNgGr"},{"id":"1343","name":"ZeDoizPcOBafZtVYDOmpzGoHekfoxf"},{"id":"7382","name":"KtqXeVdCQJlwSNHkgkxuoIGdOWrmqG"},{"id":"1365","name":"rCSTlgbmTAFhbSfPmnftcDLwdiKsHt"},{"id":"8037","name":"PUvwVYoSvSTnwjJCQITTcwNvMOpxie"},{"id":"4858","name":"cUfQfDIiyMfCMYBKGwhZSWnRRKwlxG"},{"id":"9141","name":"rJxMGOWRjdkphthcaKTspFrMcvcLLb"},{"id":"9128","name":"gcsYaolAQqrNMQTluIAKOkwYTWVUXe"},{"id":"2268","name":"jwXOUcXAiLurRlgTdxyKWvsbNHfFxl"},{"id":"5447","name":"whivfJXOdxoHtLIGpytTdbOXxlZpUY"},{"id":"7551","name":"whykuIjZUgvOFGpmNHjoPeTeYCPNby"},{"id":"719","name":"SmbiwQaORLdsbAlUZbQwgCKfuoPLVr"},{"id":"7773","name":"LZmRMXmXXHzlzFFJAopDNnWkuBqndD"},{"id":"9602","name":"xCNsDBFMygEwZuecJKTUrqeDLBJlrR"},{"id":"1536","name":"hrfeFnKnmVgZDDOxAHgXfgcJSRyiXB"},{"id":"3549","name":"NvvhXwWgCSaYijqhxsrxIWrHbBOOIa"}],"greeting":"hTAIJLspvLr8DJPG3jYh","favoriteFruit":"f6ZsZ3saRGKMBCZLAkiP"}]}
        """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] INPUT_STR = "{\"str\":\"myString\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INPUT_STR_ARRAY_LONG = "{\"strs\":[\"myString1\",\"myString2\",\"xniomb\", \"seelzp\", \"nzogdq\", \"omblsg\", \"idgtlm\", \"ydonzo\", \"needle\", \"idg\",\"myString1\",\"myString2\",\"xniomb\", \"seelzp\", \"nzogdq\", \"omblsg\", \"idgtlm\", \"ydonzo\", \"needle\", \"idg\"]}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INPUT_STR_ARRAY_SHORT = "{\"strs\":[\"myString1\",\"myString2\"]}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INPUT_INT = "{\"integer\":123}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INPUT_INT_ARRAY = "{\"integers\":[123, 456]}".getBytes(StandardCharsets.UTF_8);

    @Benchmark
    public Object decodeInputConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            HAYSTACK_6_6,
            INPUT_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeInputField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            HAYSTACK_6_6,
            INPUT_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeUsers(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_USERS,
            USERS_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeUsersNoArrays(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_USERS,
            USERS_NO_ARRAYS_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeInputSetter(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            HAYSTACK_6_6,
            INPUT_SETTER_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_STR,
            STRING_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_STR,
            STRING_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringArrayConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_STR_ARRAY_LONG,
            STRING_ARRAY_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringArrayField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_STR_ARRAY_SHORT,
            STRING_ARRAY_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringListConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_STR_ARRAY_SHORT,
            STRING_LIST_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringListFieldSmall(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_STR_ARRAY_SHORT,
            STRING_LIST_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeStringListFieldBig(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_STR_ARRAY_LONG,
            STRING_LIST_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeIntegerConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_INT,
            INTEGER_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeIntegerField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_INT,
            INTEGER_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeIntegerArrayConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_INT_ARRAY,
            INTEGER_ARRAY_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeIntegerArrayField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_INT_ARRAY,
            INTEGER_ARRAY_FIELD_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeIntConstructor(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_INT,
            INT_CONSTRUCTOR_ARGUMENT
        );
    }

    @Benchmark
    public Object decodeIntField(Holder holder) throws IOException {
        return holder.jsonMapper.readValue(
            INPUT_INT,
            INT_FIELD_ARGUMENT
        );
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(JacksonBenchmark.class.getName() + ".*")
//            .include(JacksonBenchmark.class.getName() + ".decodeUsersNoArrays")
            .warmupIterations(5)
            .measurementIterations(10)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
//            .addProfiler(AsyncProfiler.class, "libPath=/Users/denisstepanov/dev/async-profiler-2.9-macos/build/libasyncProfiler.dylib;output=flamegraph")
//            .addProfiler(AsyncProfiler.class, "libPath=/Users/denisstepanov/dev/async-profiler-2.9-macos/build/libasyncProfiler.dylib;output=flamegraph")
//            .addProfiler(AsyncProfiler.class, "libPath=/home/yawkat/bin/async-profiler-2.9-linux-x64/build/libasyncProfiler.so;output=flamegraph")
            .forks(1)
//            .jvmArgsAppend("-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints")
//            .jvmArgsPrepend("-Dio.type.pollution.file=out.txt", "-javaagent:/Users/denisstepanov/dev/micronaut-core/type-pollution-agent-0.1-SNAPSHOT.jar")
            .build();

        new Runner(opt).run();
    }

    public static void mainx(String[] args) throws Exception {
        ApplicationContext ctx = ApplicationContext.run();
        Holder holder = new Holder();
        holder.jsonMapper = ctx.getBean(JacksonJsonMapper.class);
//        holder.jsonMapper = ctx.getBean(JacksonJsonMapper.class);
        Object obj = new JacksonBenchmark().decodeStringListFieldSmall(holder);

        System.out.println(obj);
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
