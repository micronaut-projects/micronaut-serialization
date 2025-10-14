package io.micronaut.serde.support.serializers;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.annotation.SerdeConfig.SerAnyGetter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class CustomizedMapSerializerTest {

    @Serdeable
    @Introspected
    static class Holder {
        private final Map<String, Object> extra = new LinkedHashMap<>();

        @SerAnyGetter
        public Map<String, Object> getExtra() {
            return extra;
        }
    }

    @Test
    void defaultMapSerializers() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            ObjectMapper objectMapper = ctx.getBean(ObjectMapper.class);
            SerdeRegistry serdeRegistry = objectMapper.getSerdeRegistry();

            Argument<Map<String, Object>> stringKeyMap = Argument.mapOf(String.class, Object.class);
            Serializer<? super Map<String, Object>> stringKeyMapSerializer = serdeRegistry
                .findSerializer(stringKeyMap)
                .createSpecific(serdeRegistry.newEncoderContext(null), stringKeyMap);
            Assertions.assertInstanceOf(StringKeyMapSerializer.class, stringKeyMapSerializer);

            Argument<Map<CharSequence, Object>> charSeqKeyMap = Argument.mapOf(CharSequence.class, Object.class);
            Serializer<? super Map<CharSequence, Object>> charSeqKeyMapSerializer = serdeRegistry
                .findSerializer(charSeqKeyMap)
                .createSpecific(serdeRegistry.newEncoderContext(null), charSeqKeyMap);
            Assertions.assertInstanceOf(CharSequenceKeyMapSerializer.class, charSeqKeyMapSerializer);

            Argument<Map> map = Argument.of(Map.class);
            Serializer<? super Map<CharSequence, Object>> mapSerializer = serdeRegistry
                .findSerializer(map)
                .createSpecific(serdeRegistry.newEncoderContext(null), map);
            Assertions.assertInstanceOf(RuntimeMapSerializer.class, mapSerializer);
        }
    }

    @Test
    void mapSerializerRaceCondition() throws Exception {
        final int contextIterations = 100;
        for (int k = 0; k < contextIterations; k++) {
            try (ApplicationContext ctx = ApplicationContext.run()) {
                ObjectMapper objectMapper = ctx.getBean(ObjectMapper.class);
                final int iterations = 10_000;
                AtomicReference<Throwable> firstError = new AtomicReference<>();

                ExecutorService pool = Executors.newFixedThreadPool(4);

                for (int i = 0; i < iterations && firstError.get() == null; i++) {
                    CountDownLatch latch = new CountDownLatch(1);

                    pool.submit(() -> serialize(objectMapper, latch, "plain-string", firstError, """
                        {"a":"plain-string"}"""));
                    pool.submit(() -> serialize(objectMapper, latch, Map.of("nested", 1), firstError, """
                        {"a":{"nested":1}}"""));
                    pool.submit(() -> serialize(objectMapper, latch, List.of(1, 2, 3), firstError, """
                        {"a":[1,2,3]}"""));
                    pool.submit(() -> serialize(objectMapper, latch, 42, firstError, """
                        {"a":42}"""));

                    latch.countDown(); // let both tasks race
                }

                pool.shutdown();
                pool.awaitTermination(30, TimeUnit.SECONDS);

                Throwable actual = firstError.get();
                if (actual != null) {
                    // Optional: print the captured stack-trace for inspection
                    firstError.get().printStackTrace();
                }
                Assertions.assertNull(actual);
            }
        }
    }

    private static void serialize(ObjectMapper objectMapper,
                                  CountDownLatch latch,
                                  Object value,
                                  AtomicReference<Throwable> sink,
                                  String result) {
        try {
            Holder h = new Holder();
            h.getExtra().put("a", value);
            latch.await();
            Assertions.assertEquals(result, new String(objectMapper.writeValueAsBytes(h)));
        } catch (Throwable t) {
            sink.compareAndSet(null, t);              // keep the first failure only
        }
    }
}
