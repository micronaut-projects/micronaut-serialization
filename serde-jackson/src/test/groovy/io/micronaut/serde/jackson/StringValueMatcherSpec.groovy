package io.micronaut.serde.jackson

import spock.lang.Specification

class StringValueMatcherSpec extends Specification {

    void "matches values in a character range"() {
        given:
        def matcher = new StringValueMatcher(['A', 'BB', '', 'same', 'same', 'Aa', 'BB2'])

        expect:
        matcher.match(chars as char[], offset, length) == index

        where:
        chars        | offset | length | index
        'A'          | 0      | 1      | 0
        'xxBBxx'     | 2      | 2      | 1
        'xx'         | 1      | 0      | 2
        '"same"'     | 1      | 4      | 3
        'Aa'         | 0      | 2      | 5
        'BB2'        | 0      | 3      | 6
        'BB2'        | 0      | 2      | 1
        'C'          | 0      | 1      | -1
        'sam'        | 0      | 3      | -1
    }

    void "colliding hashes are told apart"() {
        given:
        // "Aa" and "BB" have the same String hash code
        def matcher = new StringValueMatcher(['Aa', 'BB', 'AaAa', 'BBBB', 'AaBB'])

        expect:
        ['Aa', 'BB', 'AaAa', 'BBBB', 'AaBB', 'BBAa'].collect { matcher.match(it as char[], 0, it.length()) } == [0, 1, 2, 3, 4, -1]
    }
}
