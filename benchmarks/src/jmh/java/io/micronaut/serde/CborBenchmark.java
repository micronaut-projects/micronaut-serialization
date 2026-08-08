package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.cbor.CborObjectMapper;
import io.micronaut.serde.data.Name;
import io.micronaut.serde.data.SimpleBean;
import io.micronaut.serde.data.User;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;
import java.util.Map;

/**
 * JMH benchmarks for Micronaut Serde CBOR (streaming factory + build-time serdes).
 *
 * <p>Run with:
 * {@code ./gradlew :micronaut-benchmarks:jmh -Pjmh.includes='.*CborBenchmark.*' -Pjmh.forks=1}</p>
 */
public class CborBenchmark {

    private static final Argument<User> USER_ARGUMENT = Argument.of(User.class);
    private static final Argument<SimpleBean> SIMPLE_BEAN_ARGUMENT = Argument.of(SimpleBean.class);

    @Benchmark
    public byte[] serializeUser(Holder holder) throws IOException {
        return holder.cborMapper.writeValueAsBytes(USER_ARGUMENT, holder.user);
    }

    @Benchmark
    public User deserializeUser(Holder holder) throws IOException {
        return holder.cborMapper.readValue(holder.userCbor, USER_ARGUMENT);
    }

    @Benchmark
    public User roundTripUser(Holder holder) throws IOException {
        byte[] bytes = holder.cborMapper.writeValueAsBytes(USER_ARGUMENT, holder.user);
        return holder.cborMapper.readValue(bytes, USER_ARGUMENT);
    }

    @Benchmark
    public byte[] serializeSimpleBean(Holder holder) throws IOException {
        return holder.cborMapper.writeValueAsBytes(SIMPLE_BEAN_ARGUMENT, holder.simpleBean);
    }

    @Benchmark
    public SimpleBean deserializeSimpleBean(Holder holder) throws IOException {
        return holder.cborMapper.readValue(holder.simpleBeanCbor, SIMPLE_BEAN_ARGUMENT);
    }

    @State(Scope.Benchmark)
    public static class Holder {
        ApplicationContext context;
        CborObjectMapper cborMapper;
        User user;
        SimpleBean simpleBean;
        byte[] userCbor;
        byte[] simpleBeanCbor;

        @Setup
        public void setup() throws IOException {
            context = ApplicationContext.run(Map.of(
                "micronaut.serde.write-binary-as-array", "false"
            ));
            cborMapper = context.getBean(CborObjectMapper.class);
            user = new User();
            user.setId(123L);
            user.setName(new Name("Foo", "Bar"));
            simpleBean = new SimpleBean();
            simpleBean.setId(1L);
            simpleBean.setName("benchmark");
            userCbor = cborMapper.writeValueAsBytes(USER_ARGUMENT, user);
            simpleBeanCbor = cborMapper.writeValueAsBytes(SIMPLE_BEAN_ARGUMENT, simpleBean);
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }
}
