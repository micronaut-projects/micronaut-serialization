package io.micronaut.serde.jmespath;

import io.micronaut.serde.jmespath.model.ArrayAllExpression;
import io.micronaut.serde.jmespath.model.ArrayItemAtExpression;
import io.micronaut.serde.jmespath.model.PathExpression;
import io.micronaut.serde.jmespath.model.KeySelectionExpression;
import io.micronaut.serde.jmespath.parser.JmesPathBaseVisitor;
import io.micronaut.serde.jmespath.parser.JmesPathParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

final class SerdeJmesPathVisitor extends JmesPathBaseVisitor<PathExpression> {

    List<PathExpression> expressions = new ArrayList<>();

    @Override
    public PathExpression visitExpression(JmesPathParser.ExpressionContext ctx) {
        if (ctx.DOT() != null) {
            expressions.add(ctx.getChild(0).accept(this));
            expressions.add(ctx.getChild(2).accept(this));
        } else {
            for (ParseTree child : ctx.children) {
                PathExpression pathExpression = child.accept(this);
                if (pathExpression != null) {
                    expressions.add(pathExpression);
                }
            }
        }
        return null;
    }

    @Override
    public PathExpression visitArrayIndexExpression(JmesPathParser.ArrayIndexExpressionContext ctx) {
        return new ArrayItemAtExpression(
            Integer.parseInt(ctx.number().getText())
        );
    }
//
//    @Override
//    public PathExpression visitArraySliceExpression(JmesPathParser.ArraySliceExpressionContext ctx) {
//        JmesPathParser.NumberContext from = ctx.number(0);
//        JmesPathParser.NumberContext to = ctx.number(1);
//        JmesPathParser.NumberContext step = ctx.number(2);
//        return new ArraySlicePredicate(
//            ctx.getChild(0).accept(this),
//            0, 0, 0
//        );
//    }

    @Override
    public PathExpression visitArrayStarExpression(JmesPathParser.ArrayStarExpressionContext ctx) {
        return new ArrayAllExpression();
    }
//
//    @Override
//    public PathExpression visitArrayFilterExpression(JmesPathParser.ArrayFilterExpressionContext ctx) {
//        return super.visitArrayFilterExpression(ctx);
//    }

    @Override
    public PathExpression visitComparator(JmesPathParser.ComparatorContext ctx) {
        return super.visitComparator(ctx);
    }

    @Override
    public PathExpression visitMultiSelectList(JmesPathParser.MultiSelectListContext ctx) {
        return super.visitMultiSelectList(ctx);
    }

    @Override
    public PathExpression visitMultiSelectHash(JmesPathParser.MultiSelectHashContext ctx) {
        return super.visitMultiSelectHash(ctx);
    }

    @Override
    public PathExpression visitPropertySelectionExpression(JmesPathParser.PropertySelectionExpressionContext ctx) {
        JmesPathParser.PropertyNameExpressionContext exp = ctx.propertyNameExpression();
        JmesPathParser.UnquotedStringContext unquotedString = exp.unquotedString();
        if (unquotedString != null) {
            return new KeySelectionExpression(unquotedString.getText());
        }
        JmesPathParser.QuotedStringContext quotedString = exp.quotedString();
        if (quotedString != null) {
            return new KeySelectionExpression(quotedString.getText());
        }
        throw new IllegalArgumentException();
    }

    @Override
    public PathExpression visitKeyValueExpression(JmesPathParser.KeyValueExpressionContext ctx) {
        return super.visitKeyValueExpression(ctx);
    }

    @Override
    public PathExpression visitFunctionExpression(JmesPathParser.FunctionExpressionContext ctx) {
        return super.visitFunctionExpression(ctx);
    }

    @Override
    public PathExpression visitNoArgs(JmesPathParser.NoArgsContext ctx) {
        return super.visitNoArgs(ctx);
    }

    @Override
    public PathExpression visitOneOrMoreArgs(JmesPathParser.OneOrMoreArgsContext ctx) {
        return super.visitOneOrMoreArgs(ctx);
    }

    @Override
    public PathExpression visitFunctionArg(JmesPathParser.FunctionArgContext ctx) {
        return super.visitFunctionArg(ctx);
    }

    @Override
    public PathExpression visitExpressionType(JmesPathParser.ExpressionTypeContext ctx) {
        return super.visitExpressionType(ctx);
    }

    @Override
    public PathExpression visitRawString(JmesPathParser.RawStringContext ctx) {
        return super.visitRawString(ctx);
    }

    @Override
    public PathExpression visitRawStringChar(JmesPathParser.RawStringCharContext ctx) {
        return super.visitRawStringChar(ctx);
    }

    @Override
    public PathExpression visitPreservedEscape(JmesPathParser.PreservedEscapeContext ctx) {
        return super.visitPreservedEscape(ctx);
    }

    @Override
    public PathExpression visitRawStringEscape(JmesPathParser.RawStringEscapeContext ctx) {
        return super.visitRawStringEscape(ctx);
    }

    @Override
    public PathExpression visitLiteral(JmesPathParser.LiteralContext ctx) {
        return super.visitLiteral(ctx);
    }

    @Override
    public PathExpression visitJsonText(JmesPathParser.JsonTextContext ctx) {
        return super.visitJsonText(ctx);
    }

    @Override
    public PathExpression visitJsonValue(JmesPathParser.JsonValueContext ctx) {
        return super.visitJsonValue(ctx);
    }

    @Override
    public PathExpression visitJsonObject(JmesPathParser.JsonObjectContext ctx) {
        return super.visitJsonObject(ctx);
    }

    @Override
    public PathExpression visitMember(JmesPathParser.MemberContext ctx) {
        return super.visitMember(ctx);
    }

    @Override
    public PathExpression visitJsonArray(JmesPathParser.JsonArrayContext ctx) {
        return super.visitJsonArray(ctx);
    }

    @Override
    public PathExpression visitJsonNumber(JmesPathParser.JsonNumberContext ctx) {
        return super.visitJsonNumber(ctx);
    }

    @Override
    public PathExpression visitJsonString(JmesPathParser.JsonStringContext ctx) {
        return super.visitJsonString(ctx);
    }

    @Override
    public PathExpression visitJsonUnescaped(JmesPathParser.JsonUnescapedContext ctx) {
        return super.visitJsonUnescaped(ctx);
    }

    @Override
    public PathExpression visitJsonEscaped(JmesPathParser.JsonEscapedContext ctx) {
        return super.visitJsonEscaped(ctx);
    }

    @Override
    public PathExpression visitEscapedChar(JmesPathParser.EscapedCharContext ctx) {
        return super.visitEscapedChar(ctx);
    }

    @Override
    public PathExpression visitUnquotedString(JmesPathParser.UnquotedStringContext ctx) {
        return super.visitUnquotedString(ctx);
    }

    @Override
    public PathExpression visitQuotedString(JmesPathParser.QuotedStringContext ctx) {
        return super.visitQuotedString(ctx);
    }

    @Override
    public PathExpression visitUnescapedChar(JmesPathParser.UnescapedCharContext ctx) {
        return super.visitUnescapedChar(ctx);
    }

    @Override
    public PathExpression visitNumber(JmesPathParser.NumberContext ctx) {
        return super.visitNumber(ctx);
    }

}
