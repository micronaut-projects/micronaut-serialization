package io.micronaut.serde.jmespath;

import io.micronaut.serde.jmespath.model.ArrayFlattenExpressionJson;
import io.micronaut.serde.jmespath.model.ArrayItemAtExpressionJson;
import io.micronaut.serde.jmespath.model.ArraySliceExpressionJson;
import io.micronaut.serde.jmespath.model.ArrayWildcardExpressionJson;
import io.micronaut.serde.jmespath.model.JsonPath;
import io.micronaut.serde.jmespath.model.JsonPathExpression;
import io.micronaut.serde.jmespath.model.KeyExpressionJson;
import io.micronaut.serde.jmespath.model.MultiSelectKeyValueExpressionJson;
import io.micronaut.serde.jmespath.model.MultiSelectListExpressionJson;
import io.micronaut.serde.jmespath.model.WildcardJsonPathExpression;
import io.micronaut.serde.jmespath.parser.JmesPathBaseListener;
import io.micronaut.serde.jmespath.parser.JmesPathParser;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    List<JsonPathExpression> expressions = new ArrayList<>();
    ArrayDeque<List<JsonPathExpression>> past = new ArrayDeque<>();

    @Override
    public void enterMultiSelectList(JmesPathParser.MultiSelectListContext ctx) {
        List<JsonPathExpression> last = expressions;
        past.push(last);
        expressions = new ArrayList<>();
        List<JsonPath> values = new ArrayList<>();
        for (JmesPathParser.ExpressionContext expressionContext : ctx.expression()) {
            ParseTreeWalker.DEFAULT.walk(this, expressionContext);
            values.add(JsonPath.of(new ArrayList<>(expressions)));
            expressions.clear();
        }

        last.add(new MultiSelectListExpressionJson(values));
    }

    @Override
    public void exitMultiSelectList(JmesPathParser.MultiSelectListContext ctx) {
        expressions = past.pop();
    }

    @Override
    public void enterMultiSelectKeyValues(JmesPathParser.MultiSelectKeyValuesContext ctx) {
        List<JsonPathExpression> last = expressions;
        past.push(last);
        expressions = new ArrayList<>();
        List<Map.Entry<String, JsonPath>> values = new ArrayList<>();
        for (JmesPathParser.KeyValueExpressionContext e : ctx.keyValueExpression()) {
            ParseTreeWalker.DEFAULT.walk(this, e.expression());
            values.add(
                Map.entry(
                    getKeyExpressionAsString(e.keyExpression()),
                    JsonPath.of(new ArrayList<>(expressions)))
            );
            expressions.clear();
        }

        last.add(new MultiSelectKeyValueExpressionJson(values));
    }

    @Override
    public void exitMultiSelectKeyValues(JmesPathParser.MultiSelectKeyValuesContext ctx) {
        expressions = past.pop();
    }

    @Override
    public void enterKeyExpression(JmesPathParser.KeyExpressionContext ctx) {
        expressions.add(new KeyExpressionJson(getKeyExpressionAsString(ctx)));
    }

    private String getKeyExpressionAsString(JmesPathParser.KeyExpressionContext ctx) {
        TerminalNode name = ctx.NAME();
        if (name != null) {
            return name.getText();
        }
        TerminalNode string = ctx.STRING();
        if (string != null) {
            return unquote(IDENTIFIER_ESCAPE_HELPER.unescape(string.getText()));
        }
        TerminalNode jsonConstant = ctx.JSON_CONSTANT();
        if (jsonConstant != null) {
            return jsonConstant.getText();
        }
        throw new IllegalArgumentException();
    }

    @Override
    public void enterWildcardExpression(JmesPathParser.WildcardExpressionContext ctx) {
//        expressions.add(new WildcardJsonPathExpression());
    }

    @Override
    public void enterWildcard(JmesPathParser.WildcardContext ctx) {
        expressions.add(new WildcardJsonPathExpression());
    }

    private String unquote(String str) {
        return str.substring(1, str.length() - 1);
    }

    @Override
    public void enterArrayIndexExpression(JmesPathParser.ArrayIndexExpressionContext ctx) {
        expressions.add(
            new ArrayItemAtExpressionJson(
                Integer.parseInt(ctx.SIGNED_INT().getText())
            )
        );
    }

    @Override
    public void enterFlattenArrayExpression(JmesPathParser.FlattenArrayExpressionContext ctx) {
        expressions.add(
            new ArrayFlattenExpressionJson()
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
            new ArrayWildcardExpressionJson()
        );
    }

    @Override
    public void enterArraySliceExpression(JmesPathParser.ArraySliceExpressionContext ctx) {
        expressions.add(getSlice(ctx));
    }

    private ArraySliceExpressionJson getSlice(JmesPathParser.ArraySliceExpressionContext ctx) {
        Long from = null;
        Long to = null;
        Long step = null;
        if (ctx.from != null) {
            from = Long.parseLong(ctx.from.getText());
        }
        if (ctx.to != null) {
            to = Long.parseLong(ctx.to.getText());
        }
        if (ctx.step != null) {
            step = Long.parseLong(ctx.step.getText());
        }
        return new ArraySliceExpressionJson(from, to, step);
    }
}
