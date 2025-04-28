package io.micronaut.serde.jmespath;

import io.micronaut.serde.jmespath.model.ArrayAllExpression;
import io.micronaut.serde.jmespath.model.ArrayItemAtExpression;
import io.micronaut.serde.jmespath.model.ArraySliceExpression;
import io.micronaut.serde.jmespath.model.PathExpression;
import io.micronaut.serde.jmespath.model.KeySelectionExpression;
import io.micronaut.serde.jmespath.parser.JmesPathBaseListener;
import io.micronaut.serde.jmespath.parser.JmesPathParser;
import jakarta.annotation.Nullable;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class SerdeJmesPathListener extends JmesPathBaseListener {

    List<PathExpression> expressions = new ArrayList<>();

    @Override
    public void enterPropertySelectionExpression(JmesPathParser.PropertySelectionExpressionContext ctx) {
        JmesPathParser.PropertyNameExpressionContext exp = ctx.propertyNameExpression();
        JmesPathParser.UnquotedStringContext unquotedString = exp.unquotedString();
        if (unquotedString != null) {
            expressions.add(new KeySelectionExpression(unquotedString.getText()));
            return;
        }
        JmesPathParser.QuotedStringContext quotedString = exp.quotedString();
        if (quotedString != null) {
            expressions.add(new KeySelectionExpression(quotedString.getText()));
        }
        throw new IllegalArgumentException();
    }

    @Override
    public void enterArrayIndexExpression(JmesPathParser.ArrayIndexExpressionContext ctx) {
        expressions.add(
            new ArrayItemAtExpression(
                toNumber(ctx.number())
            )
        );
    }

    @Nullable
    private Integer toNumber(@Nullable JmesPathParser.NumberContext ctx) {
        return ctx == null ? null : Integer.parseInt(ctx.getText());
    }

    @Nullable
    private Long toLong(@Nullable JmesPathParser.NumberContext ctx) {
        return ctx == null ? null : Long.parseLong(ctx.getText());
    }

    @Override
    public void enterArrayStarExpression(JmesPathParser.ArrayStarExpressionContext ctx) {
        expressions.add(
            new ArrayAllExpression()
        );
    }

    @Override
    public void enterArraySliceExpression(JmesPathParser.ArraySliceExpressionContext ctx) {
        expressions.add(getSlice(ctx));
    }

    private ArraySliceExpression getSlice(JmesPathParser.ArraySliceExpressionContext ctx) {
        Iterator<ParseTree> iterator = ctx.children.iterator();
        iterator.next();
        ParseTree child = iterator.next(); // skip LBRACK
        Long from = null;
        if (child instanceof JmesPathParser.NumberContext numberContext) {
            from = toLong(numberContext);
        } // else Colon
        child = iterator.next();
        if (child == ctx.RBRACK()) {
            return new ArraySliceExpression(from, null, null);
        }
        Long to = null;
        if (child instanceof JmesPathParser.NumberContext numberContext) {
            to = toLong(numberContext);
        } // else Colon
        child = iterator.next();
        if (child == ctx.RBRACK()) {
            return new ArraySliceExpression(from, to, null);
        }
        if (child instanceof JmesPathParser.NumberContext numberContext) {
            return new ArraySliceExpression(from, to, toLong(numberContext));
        }
        throw new IllegalArgumentException();
    }
}
