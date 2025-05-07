package io.micronaut.serde.jmespath

import io.micronaut.serde.jmespath.model.ArrayWildcardExpressionJson
import io.micronaut.serde.jmespath.model.ArrayItemAtExpressionJson
import io.micronaut.serde.jmespath.model.ArraySliceExpressionJson
import io.micronaut.serde.jmespath.model.KeyExpressionJson
import spock.lang.Specification

class ParserSpec extends Specification {

    def propertySelection() {
        when:
            def expression = SerdeJmesPathParser.parse(query)[0] as KeyExpressionJson
        then:
            expression.key() == value

        where:
            query                  || value
            "foo"                  || "foo"
            "special chars: !@#"   || "special chars: !@#"
            "quote\"char"          || "quote\"char"
            "\u2713"               || "\u2713"
            '""with space"'        || "with space"
            '"special chars: !@#"' || "special chars: !@#"
            '"quote\"char"'        || "quote\"char"
            '"\u2713"'             || "\u2713"
    }

    def subExpression() {
        when:
            def expressions = SerdeJmesPathParser.parse("foo.bar")
        then:
            (expressions[0] as KeyExpressionJson).key() == "foo"
            (expressions[1] as KeyExpressionJson).key() == "bar"

    }

    def subArrayExpression() {
        when:
            def expressions = SerdeJmesPathParser.parse("foo.bar[3]")
        then:
            (expressions[0] as KeyExpressionJson).key() == "foo"
            (expressions[1] as KeyExpressionJson).key() == "bar"
            (expressions[2] as ArrayItemAtExpressionJson).index() == 3

    }

    def subArrayExpressionStar() {
        when:
            def expressions = SerdeJmesPathParser.parse("foo.bar[*]")
        then:
            (expressions[0] as KeyExpressionJson).key() == "foo"
            (expressions[1] as KeyExpressionJson).key() == "bar"
            (expressions[2] as ArrayWildcardExpressionJson)

    }

    def sliceArrayExpressionStar() {
        when:
            def expressions = SerdeJmesPathParser.parse(query)
        then:
            (expressions[0] as KeyExpressionJson).key() == "foo"
            (expressions[1] as KeyExpressionJson).key() == "bar"
            def slice = (expressions[2] as ArraySliceExpressionJson)
            slice.from() == from
            slice.to() == to
            slice.step() == step
        where:
            query            || from || to   || step
//            "foo.bar[0:4:1]" || 0    || 4    || 1
//            "foo.bar[0:4]"   || 0    || 4    || null
//            "foo.bar[0:3]"   || 0    || 3    || null
            "foo.bar[:2]"    || null || 2    || null
            "foo.bar[::2]"   || null || null || 2
            "foo.bar[::-1]"  || null || null || -1
            "foo.bar[-2:]"   || -2   || null || null


    }

    def arrayExpression() {
        when:
            def expressions = SerdeJmesPathParser.parse("foo[1]")
        then:
            (expressions[0] as KeyExpressionJson).key() == "foo"
            (expressions[1] as ArrayItemAtExpressionJson).index() == 1

    }

}
