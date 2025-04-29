package io.micronaut.serde.jmespath;

import io.micronaut.serde.jmespath.model.ArrayAllExpression;
import io.micronaut.serde.jmespath.model.ArrayFlattenExpression;
import io.micronaut.serde.jmespath.model.ArrayItemAtExpression;
import io.micronaut.serde.jmespath.model.ArraySliceExpression;
import io.micronaut.serde.jmespath.model.KeySelectionExpression;
import io.micronaut.serde.jmespath.model.PathExpression;
import io.micronaut.serde.jmespath.parser.JmesPathBaseListener;
import io.micronaut.serde.jmespath.parser.JmesPathParser;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

final class SerdeJmesPathListener extends JmesPathBaseListener {
    private static final StringEscapeHelper IDENTIFIER_ESCAPE_HELPER = new StringEscapeHelper(
        true,
        '"', '"',
        '/', '/',
        '\\', '\\',
        'b', '\b',
        'f', '\f',
        'n', '\n',
        'r', '\r',
        't', '\t'
    );

    private static final StringEscapeHelper RAW_STRING_ESCAPE_HELPER = new StringEscapeHelper(
        false,
        '\'', '\'',
        '\\', '\\'
    );

    private static final StringEscapeHelper JSON_LITERAL_ESCAPE_HELPER = new StringEscapeHelper(
        false,
        '`', '`'
    );

    List<PathExpression> expressions = new ArrayList<>();

    @Override
    public void enterPropertySelectionExpression(JmesPathParser.PropertySelectionExpressionContext ctx) {
//        JmesPathParser.PropertyNameExpressionContext exp = ctx.propertyNameExpression();
//        JmesPathParser.UnquotedStringContext unquotedString = exp.unquotedString();
//        if (unquotedString != null) {
//            expressions.add(new KeySelectionExpression(unquotedString.getText()));
//            return;
//        }
//        JmesPathParser.QuotedStringContext quotedString = exp.quotedString();
//        if (quotedString != null) {
        TerminalNode name = ctx.NAME();
        if (name != null) {
            expressions.add(new KeySelectionExpression(name.getText()));
            return;
        }
        TerminalNode string = ctx.STRING();
        if (string != null) {
            expressions.add(new KeySelectionExpression(unquote(IDENTIFIER_ESCAPE_HELPER.unescape(string.getText()))));
            return;
        }
        TerminalNode jsonConstant = ctx.JSON_CONSTANT();
        if (jsonConstant != null) {
            expressions.add(new KeySelectionExpression(jsonConstant.getText()));
            return;
        }
        throw new IllegalArgumentException();
    }

    private String unquote(String str) {
        return str.substring(1, str.length() - 1);
    }

    @Override
    public void enterArrayIndexExpression(JmesPathParser.ArrayIndexExpressionContext ctx) {
        expressions.add(
            new ArrayItemAtExpression(
                Integer.parseInt(ctx.SIGNED_INT().getText())
            )
        );
    }

    @Override
    public void enterFlattenArrayExpression(JmesPathParser.FlattenArrayExpressionContext ctx) {
        expressions.add(
            new ArrayFlattenExpression()
        );
    }

    //    @Nullable
//    private Integer toNumber(@Nullable JmesPathParser.NumberContext ctx) {
//        return ctx == null ? null : Integer.parseInt(ctx.getText());
//    }
//
//    @Nullable
//    private Long toLong(@Nullable JmesPathParser.NumberContext ctx) {
//        return ctx == null ? null : Long.parseLong(ctx.getText());
//    }

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
//        Iterator<ParseTree> iterator = ctx.children.iterator();
//        iterator.next();
//        ParseTree child = iterator.next(); // skip LBRACK
//        Long from = null;
//        if (child instanceof JmesPathParser.NumberContext numberContext) {
//            from = toLong(numberContext);
//        } // else Colon
//        child = iterator.next();
//        if (child == ctx.RBRACK()) {
//            return new ArraySliceExpression(from, null, null);
//        }
//        Long to = null;
//        if (child instanceof JmesPathParser.NumberContext numberContext) {
//            to = toLong(numberContext);
//        } // else Colon
//        child = iterator.next();
//        if (child == ctx.RBRACK()) {
//            return new ArraySliceExpression(from, to, null);
//        }
//        if (child instanceof JmesPathParser.NumberContext numberContext) {
//            return new ArraySliceExpression(from, to, toLong(numberContext));
//        }
        throw new IllegalArgumentException();
    }
}
