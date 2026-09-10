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
package io.micronaut.serde.toon.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.exceptions.SerdeException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Quote-aware lexical utilities shared by {@link ToonEncoder} (encode) and
 * {@link ToonDecoder} (decode).
 *
 * @see <a href="https://github.com/toon-format/spec">TOON specification</a>
 * @since 3.2.0
 */
@Internal
public final class ToonEscapes {

    /**
     * The encode-side quoting check for whether a string value looks
     * number-like enough to need quoting. Deliberately broader than {@link
     * #DECODE_NUMBER} (allows a leading {@code +} and leading zeros): a
     * string need only look like a number to some decoder to require
     * quoting, whether or not it matches this module's own strict decode
     * grammar.
     */
    private static final Pattern NUMERIC_LITERAL = Pattern.compile("^[+-]?[0-9]+(?:\\.[0-9]+)?(?:e[+-]?[0-9]+)?$", Pattern.CASE_INSENSITIVE);

    /**
     * The strict decode number grammar: unlike {@link #NUMERIC_LITERAL} (used
     * on encode to decide whether a string value needs quoting), this
     * disallows a leading {@code +} and forbidden leading zeros (e.g. "05" is
     * not a number, but "0", "0.5", and "0e1" are).
     */
    private static final Pattern DECODE_NUMBER = Pattern.compile("^-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:e[+-]?[0-9]+)?$", Pattern.CASE_INSENSITIVE);

    private static final char CONTROL_CHARACTER_BOUNDARY = 0x20;

    private ToonEscapes() {
    }

    /**
     * Quotes and escapes the given string value if the TOON specification
     * requires it in the context of the given active delimiter, otherwise
     * returns it unchanged.
     *
     * @param value     The unquoted string value
     * @param delimiter The active delimiter for the current context
     * @return The value, quoted and escaped if required
     */
    public static String quoteValueIfNeeded(String value, char delimiter) {
        return needsQuoting(value, delimiter) ? quote(value) : value;
    }

    public static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Returns whether the given character is valid as the first character of an unquoted key.
     *
     * @param c The character to check
     * @return {@code true} if the character is an ASCII letter or underscore
     */
    public static boolean isKeyStart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }

    /**
     * Returns whether the given character is valid as a subsequent character of an unquoted key.
     *
     * @param c The character to check
     * @return {@code true} if the character is an ASCII letter, digit, underscore, or dot
     */
    public static boolean isKeyPart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.';
    }

    /**
     * Returns whether the given key is a valid unquoted TOON key
     * ({@code ^[A-Za-z_][A-Za-z0-9_.]*$}).
     *
     * @param key The key to check
     * @return {@code true} if the key is valid without quotes
     */
    public static boolean isValidUnquotedKey(String key) {
        if (key.isEmpty() || !isKeyStart(key.charAt(0))) {
            return false;
        }

        for (int i = 1; i < key.length(); i++) {
            if (!isKeyPart(key.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Quotes and escapes the given key if it does not match the unquoted key
     * grammar ({@code ^[A-Za-z_][A-Za-z0-9_.]*$}), otherwise returns it
     * unchanged.
     *
     * @param key The unquoted key
     * @return The key, quoted and escaped if required
     */
    public static String quoteKeyIfNeeded(String key) {
        return isValidUnquotedKey(key) ? key : quote(key);
    }

    /**
     * Returns whether the given unquoted token matches the strict decode
     * number grammar, and should therefore decode as a number rather than a
     * string.
     *
     * @param token The unquoted token
     * @return {@code true} if the token should decode as a number
     */
    public static boolean isNumberToken(String token) {
        return DECODE_NUMBER.matcher(token).matches();
    }

    /**
     * Unescapes a fully-quoted token, including its surrounding quote
     * characters, decoding {@code \\ \" \n \r \t} and {@code \\u} followed by four hex digits.
     *
     * @param quoted The token, including its leading and trailing quote characters
     * @return The unescaped string value
     * @throws SerdeException If the token contains an invalid or unterminated escape sequence
     */
    public static String unquote(String quoted) throws SerdeException {
        String inner = quoted.substring(1, quoted.length() - 1);
        StringBuilder sb = new StringBuilder(inner.length());
        int i = 0;
        while (i < inner.length()) {
            char c = inner.charAt(i);
            if (c != '\\') {
                sb.append(c);
                i++;
                continue;
            }

            if (i + 1 >= inner.length()) {
                throw new SerdeException("Unterminated escape sequence: " + quoted);
            }

            char next = inner.charAt(i + 1);
            switch (next) {
                case '\\' -> {
                    sb.append('\\');
                    i += 2;
                }

                case '"' -> {
                    sb.append('"');
                    i += 2;
                }

                case 'n' -> {
                    sb.append('\n');
                    i += 2;
                }

                case 'r' -> {
                    sb.append('\r');
                    i += 2;
                }

                case 't' -> {
                    sb.append('\t');
                    i += 2;
                }

                case 'u' -> {
                    if (i + 6 > inner.length()) {
                        throw new SerdeException("Invalid \\u escape sequence: " + quoted);
                    }

                    String hex = inner.substring(i + 2, i + 6);
                    try {
                        sb.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException e) {
                        throw new SerdeException("Invalid \\u escape sequence: " + quoted);
                    }

                    i += 6;
                }

                default ->
                    throw new SerdeException("Invalid escape sequence '\\" + next + "': " + quoted);
            }
        }

        return sb.toString();
    }

    /**
     * Finds the index of the unescaped closing quote matching the opening
     * quote at {@code startQuoteIndex}.
     *
     * @param s               The string to scan
     * @param startQuoteIndex The index of the opening {@code "} character
     * @return The index of the matching closing quote
     * @throws SerdeException If the quoted string is unterminated
     */
    public static int findClosingQuote(String s, int startQuoteIndex) throws SerdeException {
        int i = startQuoteIndex + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }

            if (c == '"') {
                return i;
            }

            i++;
        }

        throw new SerdeException("Unterminated quoted string: " + s.substring(startQuoteIndex));
    }

    /**
     * Splits {@code s} on the given delimiter, treating any quoted
     * substring as opaque (a delimiter character inside a quoted token is
     * not a split point), and trims surrounding spaces from each resulting
     * token.
     *
     * @param s         The string to split
     * @param delimiter The delimiter to split on
     * @return The tokens, in order
     * @throws SerdeException If a quoted substring is unterminated
     */
    public static List<String> splitByDelimiter(String s, char delimiter) throws SerdeException {
        List<String> tokens = new ArrayList<>();

        int i = 0;
        int start = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') {
                i = findClosingQuote(s, i) + 1;
            } else if (c == delimiter) {
                tokens.add(s.substring(start, i).trim());
                i++;
                start = i;
            } else {
                i++;
            }
        }

        tokens.add(s.substring(start).trim());
        return tokens;
    }

    /**
     * Strips trailing whitespace (spaces and tabs) from the given line.
     *
     * @param line The line to strip trailing whitespace from
     * @return The line without trailing spaces or tabs
     */
    public static String stripTrailingWhitespace(String line) {
        int end = line.length();
        while (end > 0 && isSpaceOrTab(line.charAt(end - 1))) {
            end--;
        }

        return line.substring(0, end);
    }

    /**
     * Counts the number of leading space characters in the given line,
     * ensuring no tabs are used for indentation.
     *
     * @param line The line to inspect
     * @return The number of leading space characters
     * @throws SerdeException If a tab character is used for indentation
     */
    public static int countLeadingSpaces(String line) throws SerdeException {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }

        if (i < line.length() && line.charAt(i) == '\t') {
            throw new SerdeException("Tabs are not permitted for indentation: " + line);
        }

        return i;
    }

    /**
     * Returns whether the given line is a full-line comment, i.e. its first
     * non-space character is {@code #}. Comment lines are stripped before
     * structural parsing and are exempt from indentation consistency
     * checks applied to every other line.
     *
     * @param line          The line, with trailing whitespace already stripped
     * @param leadingSpaces The number of leading space characters in {@code line}
     * @return {@code true} if the line is a full-line comment
     */
    public static boolean isCommentLine(String line, int leadingSpaces) {
        return leadingSpaces < line.length() && line.charAt(leadingSpaces) == '#';
    }

    /**
     * Returns whether the given line is a full-line comment, i.e. its first
     * non-space character is {@code #}.
     *
     * @param line The line to check
     * @return {@code true} if the line is a full-line comment
     * @throws SerdeException If a tab character is used for indentation
     */
    public static boolean isCommentLine(String line) throws SerdeException {
        return isCommentLine(line, countLeadingSpaces(line));
    }

    /**
     * Returns whether the given unindented content string represents a scalar value
     * rather than an object field or an array/tabular header (i.e. not beginning with
     * {@code [} or containing an unquoted {@code :} or {@code [}).
     *
     * @param content The line content to check
     * @return {@code true} if the content looks like a scalar value
     * @throws SerdeException If the content contains an unterminated quoted string
     */
    public static boolean looksLikeScalar(String content) throws SerdeException {
        if (content.startsWith("[")) {
            return false;
        }

        int i;
        if (content.startsWith("\"")) {
            i = findClosingQuote(content, 0) + 1;
        } else {
            i = 0;
            while (i < content.length() && content.charAt(i) != ':' && content.charAt(i) != '[') {
                i++;
            }
        }

        return i >= content.length() || (content.charAt(i) != ':' && content.charAt(i) != '[');
    }

    private static boolean needsQuoting(String value, char delimiter) {
        if (value.isEmpty()) {
            return true;
        }

        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if (isSpaceOrTab(first) || isSpaceOrTab(last)) {
            return true;
        }

        if (value.equals("true") || value.equals("false") || value.equals("null")) {
            return true;
        }

        if (first == '-' || first == '#') {
            return true;
        }

        if (NUMERIC_LITERAL.matcher(value).matches()) {
            return true;
        }

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == delimiter || c == ':' || c == '"' || c == '\\' || c == '[' || c == ']' || c == '{' || c == '}' || c < CONTROL_CHARACTER_BOUNDARY) {
                return true;
            }
        }

        return false;
    }

    private static boolean isSpaceOrTab(char c) {
        return c == ' ' || c == '\t';
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    // Convert non-printable characters to their Unicode escape sequences.
                    if (c < CONTROL_CHARACTER_BOUNDARY) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }

        sb.append('"');
        return sb.toString();
    }
}
