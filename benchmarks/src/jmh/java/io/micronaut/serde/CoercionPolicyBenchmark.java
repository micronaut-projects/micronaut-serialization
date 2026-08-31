/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.SerdeableGenerated;
import io.micronaut.serde.jackson.JacksonJsonMapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Guards that restricting coercions costs nothing at read time. The policy is reduced to one
 * precalculated bit set per target type before any value is read, so a strict decoder runs the
 * same instructions as a lenient one and only the mask constant differs. A difference between the
 * two modes here means that the strategy is being derived per value again.
 * <p>
 * Every value in the payloads has the shape of the property it is read into, so neither mode
 * takes a coercion or an error path.
 */
public class CoercionPolicyBenchmark {

    private static final byte[] INT_JSON = """
        {"a":1000,"b":2000,"c":3000,"d":4000,"e":5000,"f":6000,"g":7000,"h":8000,"i":9000,"j":10000}
        """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] STRING_JSON = """
        {"a":"value-1000","b":"value-2000","c":"value-3000","d":"value-4000","e":"value-5000","f":"value-6000","g":"value-7000","h":"value-8000","i":"value-9000","j":"value-10000"}
        """.getBytes(StandardCharsets.UTF_8);

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object deserializeInts(Holder holder) throws IOException {
        return holder.mapper.readValue(INT_JSON, holder.intArgument);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object deserializeStrings(Holder holder) throws IOException {
        return holder.mapper.readValue(STRING_JSON, holder.stringArgument);
    }

    @State(Scope.Thread)
    public static class Holder {

        @Param({"LENIENT", "STRICT"})
        String coercionMode = "LENIENT";

        final Argument<IntBean> intArgument = Argument.of(IntBean.class);
        final Argument<StringBean> stringArgument = Argument.of(StringBean.class);

        JsonMapper mapper;
        ApplicationContext context;

        @Setup
        public void setUp() {
            Map<String, Object> properties = new HashMap<>();
            properties.put("micronaut.serde.serialization.inclusion", "ALWAYS");
            properties.put("micronaut.serde.deserialization.coercion-mode", coercionMode);
            context = ApplicationContext.run(properties);
            mapper = context.getBean(JacksonJsonMapper.class);
        }

        @TearDown
        public void tearDown() {
            if (context != null) {
                context.close();
            }
        }
    }

    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    @SerdeableGenerated
    public static class IntBean {
        public int a;
        public int b;
        public int c;
        public int d;
        public int e;
        public int f;
        public int g;
        public int h;
        public int i;
        public int j;
    }

    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    @SerdeableGenerated
    public static class StringBean {
        public String a;
        public String b;
        public String c;
        public String d;
        public String e;
        public String f;
        public String g;
        public String h;
        public String i;
        public String j;
    }
}
