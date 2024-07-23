package io.micronaut.serde;

import io.micronaut.test.typepollution.FocusListener;
import io.micronaut.test.typepollution.ThresholdFocusListener;
import io.micronaut.test.typepollution.TypePollutionTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeThrashingTest {
    static final int THRESHOLD = 10_000;

    private ThresholdFocusListener focusListener;

    @BeforeAll
    static void setupAgent() {
        TypePollutionTransformer.install(net.bytebuddy.agent.ByteBuddyAgent.install());
    }

    @BeforeEach
    void setUp() {
        focusListener = new ThresholdFocusListener();
        FocusListener.setFocusListener(focusListener);
    }

    @AfterEach
    void verifyNoTypeThrashing() {
        FocusListener.setFocusListener(null);
        Assertions.assertTrue(focusListener.checkThresholds(THRESHOLD), "Threshold exceeded, check logs.");
    }

    @Test
    public void testFromJmh() throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(Stream.of(ComboBenchmark.class)
                .map(Class::getName)
                .collect(Collectors.joining("|", "(", ")"))
                + ".*")
            .warmupIterations(0)
            .measurementIterations(1)
            .mode(Mode.SingleShotTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .forks(0)
            .measurementBatchSize(THRESHOLD * 2)
            .shouldFailOnError(true)
            .build();

        new Runner(opt).run();
    }
}
