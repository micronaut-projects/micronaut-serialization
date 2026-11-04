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
package io.micronaut.serde.csv;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serializable representation of a currency + amount cell such as {@code "USD 0.65"} or {@code "$0.65"}.
 */
@Serdeable
public record Total(
    String currency,
    Number amount
) {

    private static final Pattern TOTAL_PATTERN = Pattern.compile("^([^\\s\\d.]+)\\s*([0-9]+(?:\\.[0-9]+)?)$");

    /**
     * Parses a currency+amount string. The leading token (a currency symbol such as {@code '$'} or a
     * currency code) is the currency, and the trailing number is the amount.
     */
    @JsonCreator
    public static Total parse(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = TOTAL_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid currency+amount: " + value);
        }
        return new Total(matcher.group(1), new BigDecimal(matcher.group(2)));
    }

    public String format() {
        if ("$".equals(currency)) {
            return "$" + amount;
        }
        return currency + " " + amount;
    }
}
