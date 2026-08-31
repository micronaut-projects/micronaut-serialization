package io.micronaut.serde.protobuf;

import io.micronaut.serde.protobuf.wire.ProtoOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Strings are encoded straight into the output buffer rather than through an intermediate byte
 * array, so the encoding has to agree with the JDK's exactly &mdash; including for the awkward
 * cases: multi-byte characters, characters outside the basic plane, and lone surrogates.
 */
class ProtoOutputStringTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "plain ascii",
        "café naïve",           // two-byte sequences
        "你好世界",       // three-byte sequences
        "emoji 🚀🌍", // surrogate pairs, four bytes each
        "mixed é 世 🚀 tail",
        "\ud800",                          // a lone high surrogate
        "\udc00",                          // a lone low surrogate
        "before \ud800 after",             // a lone surrogate mid-string
        "pair then lone 🚀\ud800"
    })
    void matchesTheJdkEncoding(String value) {
        assertArrayEquals(expected(value), actual(value), () -> "for " + describe(value));
    }

    @Test
    void matchesTheJdkEncodingForLongStrings() {
        // long enough that the length prefix needs more than one byte
        String value = "世".repeat(500) + "x".repeat(500);
        assertArrayEquals(expected(value), actual(value));
    }

    private static byte[] expected(String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        ProtoOutput prefix = new ProtoOutput();
        prefix.writeVarint(utf8.length);
        byte[] header = prefix.toByteArray();
        byte[] result = Arrays.copyOf(header, header.length + utf8.length);
        System.arraycopy(utf8, 0, result, header.length, utf8.length);
        return result;
    }

    private static byte[] actual(String value) {
        ProtoOutput out = new ProtoOutput();
        out.writeString(value);
        return out.toByteArray();
    }

    private static String describe(String value) {
        StringBuilder sb = new StringBuilder();
        value.chars().forEach(c -> sb.append(String.format("\\u%04x", c)));
        return sb.toString();
    }
}
