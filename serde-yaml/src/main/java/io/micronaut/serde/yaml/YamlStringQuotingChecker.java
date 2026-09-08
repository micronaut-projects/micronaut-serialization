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
package io.micronaut.serde.yaml;

import io.micronaut.core.annotation.Internal;
import jakarta.inject.Singleton;

import java.util.Set;

/**
 * Helper class that defines API used by {@link YamlEncoder}
 * to check whether property names and String values need to be quoted or not.
 *
 * @since 3.2.0
 */
@Internal
@Singleton
final class YamlStringQuotingChecker {
    /**
     * As per <a href="https://yaml.org/type/bool.html">YAML Spec</a> there are a few
     * aliases for booleans, and we better quote such values as keys; although Jackson
     * itself has no problems dealing with them, some other tools do have.
     */
    private static final Set<String> RESERVED_KEYWORDS = Set.of(
        "false", "False", "FALSE",
        "n", "N",
        "no", "No", "NO",
        "null", "Null", "NULL",
        "on", "On", "ON",
        "off", "Off", "OFF",
        "true", "True", "TRUE",
        "y", "Y",
        "yes", "Yes", "YES"
    );

    /**
     * Method called by {@link YamlEncoder}.
     * to check whether given property name should be quoted: usually
     * to prevent it from being read as non-String key (boolean or number)
     */
    public boolean needToQuoteName(String name) {
        return isReservedKeyword(name) || looksLikeYAMLNumber(name)
            || nameHasQuotableChar(name);
    }

    /**
     * Method called by {@link YamlEncoder}.
     * to check whether given String value should be quoted: usually
     * to prevent it from being value of different type (boolean or number)
     */
    public boolean needToQuoteValue(String value) {
        return isReservedKeyword(value) || valueHasQuotableChar(value);
    }

    /**
     * Helper method to see if given String value is one of:
     * <ul>
     * <li>YAML 1.1 keyword representing
     *  <a href="https://yaml.org/type/bool.html">boolean</a>
     *  </li>
     * <li>YAML 1.1 keyword representing
     *  <a href="https://yaml.org/type/null.html">null</a> value
     *   </li>
     * <li>empty String (length 0)
     *   </li>
     * </ul>
     * and returns {@code true} if so.
     *
     * @param value String to check
     *
     * @return {@code true} if given value is a Boolean or Null representation
     *   (as per YAML 1.1 specification) or empty String
     */
    private static boolean isReservedKeyword(String value) {
        return value.isEmpty() || isReservedKeyword(value.charAt(0), value);
    }

    private static boolean isReservedKeyword(char firstChar, String name) {
        return switch (firstChar) {
            // false, no/n/null, on/off, true, yes/y and their capitalised spellings
            case 'f', 'n', 'o', 't', 'y', 'F', 'N', 'O', 'T', 'Y' -> RESERVED_KEYWORDS.contains(name);
            // null alias (see [dataformats-text#274])
            case '~' -> true;
            default -> false;
        };
    }

    /**
     * Helper method to see if given String value looks like a YAML 1.1 numeric value and would likely be considered
     * a number when parsing unless quoting is used.
     */
    private static boolean looksLikeYAMLNumber(String name) {
        if (name.isEmpty()) {
            return false;
        }
        return switch (name.charAt(0)) {
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+', '.' -> true;
            default -> false;
        };
    }

    /**
     * As per YAML <a href="https://yaml.org/spec/1.2/spec.html#id2788859">Plain Style</a>unquoted
     * strings are restricted to a reduced charset and must be quoted in case they contain
     * one of the following characters or character combinations.
     */
    private boolean valueHasQuotableChar(String inputStr) {
        final int end = inputStr.length();
        for (int i = 0; i < end; ++i) {
            switch (inputStr.charAt(i)) {
                case '[':
                case ']':
                case '{':
                case '}':
                case ',':
                    return true;
                case '#':
                    if (precededOnlyByBlank(inputStr, i)) {
                        return true;
                    }
                    break;
                case ':':
                    if (followedOnlyByBlank(inputStr, i)) {
                        return true;
                    }
                    break;
                default:
            }
        }
        return false;
    }

    private boolean precededOnlyByBlank(String inputStr, int offset) {
        if (offset == 0) {
            return true;
        }
        return isBlank(inputStr.charAt(offset - 1));
    }

    private boolean followedOnlyByBlank(String inputStr, int offset) {
        if (offset == inputStr.length() - 1) {
            return true;
        }
        return isBlank(inputStr.charAt(offset + 1));
    }

    private boolean isBlank(char value) {
        return ' ' == value || '\t' == value;
    }

    /**
     * Looks like we may get names with "funny characters" so.
     */
    private boolean nameHasQuotableChar(String inputStr) {
        final int end = inputStr.length();
        for (int i = 0; i < end; ++i) {
            int ch = inputStr.charAt(i);
            if (ch < 0x0020) {
                return true;
            }
        }
        return false;
    }
}
