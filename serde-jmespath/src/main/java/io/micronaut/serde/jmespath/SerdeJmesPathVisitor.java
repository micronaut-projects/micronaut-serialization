//package io.micronaut.serde.jmespath;
//
//import io.micronaut.serde.jmespath.model.ArrayAllExpression;
//import io.micronaut.serde.jmespath.model.ArrayItemAtExpression;
//import io.micronaut.serde.jmespath.model.PathExpression;
//import io.micronaut.serde.jmespath.model.KeySelectionExpression;
//import io.micronaut.serde.jmespath.parser.JmesPathBaseVisitor;
//import io.micronaut.serde.jmespath.parser.JmesPathParser;
//import org.antlr.v4.runtime.tree.ParseTree;
//
//import java.util.ArrayList;
//import java.util.List;
//
//final class SerdeJmesPathVisitor extends JmesPathBaseVisitor<PathExpression> {
//
//    List<PathExpression> expressions = new ArrayList<>();
//
//    @Override
//    public PathExpression visitExpression(JmesPathParser.ExpressionContext ctx) {
//        if (ctx.DOT() != null) {
//            expressions.add(ctx.getChild(0).accept(this));
//            expressions.add(ctx.getChild(2).accept(this));
//        } else {
//            for (ParseTree child : ctx.children) {
//                PathExpression pathExpression = child.accept(this);
//                if (pathExpression != null) {
//                    expressions.add(pathExpression);
//                }
//            }
//        }
//        return null;
//    }
//
//    @Override
//    public PathExpression visitArrayIndexExpression(JmesPathParser.ArrayIndexExpressionContext ctx) {
//        return new ArrayItemAtExpression(
//            Integer.parseInt(ctx.number().getText())
//        );
//    }
////
////    @Override
////    public PathExpression visitArraySliceExpression(JmesPathParser.ArraySliceExpressionContext ctx) {
////        JmesPathParser.NumberContext from = ctx.number(0);
////        JmesPathParser.NumberContext to = ctx.number(1);
////        JmesPathParser.NumberContext step = ctx.number(2);
////        return new ArraySlicePredicate(
////            ctx.getChild(0).accept(this),
////            0, 0, 0
////        );
////    }
//
//    @Override
//    public PathExpression visitArrayStarExpression(JmesPathParser.ArrayStarExpressionContext ctx) {
//        return new ArrayAllExpression();
//    }
//
//    @Override
//    public PathExpression visitPropertySelectionExpression(JmesPathParser.PropertySelectionExpressionContext ctx) {
//        JmesPathParser.PropertyNameExpressionContext exp = ctx.propertyNameExpression();
//        JmesPathParser.UnquotedStringContext unquotedString = exp.unquotedString();
//        if (unquotedString != null) {
//            return new KeySelectionExpression(unquotedString.getText());
//        }
//        JmesPathParser.QuotedStringContext quotedString = exp.quotedString();
//        if (quotedString != null) {
//            return new KeySelectionExpression(quotedString.getText());
//        }
//        throw new IllegalArgumentException();
//    }
//
//}
