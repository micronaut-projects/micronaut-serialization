package io.micronaut.serde.jmespath;

import io.micronaut.serde.jmespath.model.PathExpression;
import io.micronaut.serde.jmespath.parser.JmesPathLexer;
import io.micronaut.serde.jmespath.parser.JmesPathParser;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.util.BitSet;
import java.util.List;

public class SerdeJmesPathParser {

    public static List<PathExpression> parse(String query) {
        var inputStream = CharStreams.fromString(query);
        var lexer = new JmesPathLexer(inputStream);
        ANTLRErrorListener errorListener = new ANTLRErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new RuntimeException("Failed to parse query: " + prettifyAntlrError(offendingSymbol, line, charPositionInLine, msg, e, query));
            }

            @Override
            public void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean b, BitSet bitSet, ATNConfigSet atnConfigSet) {
            }

            @Override
            public void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitSet, ATNConfigSet atnConfigSet) {
            }

            @Override
            public void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atnConfigSet) {
            }
        };
        var tokenStream = new CommonTokenStream(lexer);
        var parser = new JmesPathParser(tokenStream);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        parser.addErrorListener(errorListener);
//        SerdeJmesPathVisitor visitor = new SerdeJmesPathVisitor();
        SerdeJmesPathListener listener = new SerdeJmesPathListener();
        ParseTreeWalker.DEFAULT.walk(listener, parser.expression());
        return listener.expressions;
    }

    private static String prettifyAntlrError(Object offendingSymbol,
                                             int line,
                                             int charPositionInLine,
                                             String message,
                                             RecognitionException e,
                                             String query) {
        String errorText = "At " + line + ":" + charPositionInLine;
        if (offendingSymbol instanceof CommonToken commonToken) {
            String token = commonToken.getText();
            if (token != null && !token.isEmpty()) {
                errorText += " and token '" + token + "'";
            }
        }
        errorText += ", ";
        if (e instanceof NoViableAltException) {
            errorText += message.substring(0, message.indexOf('\''));
            if (query.isEmpty()) {
                errorText += "'*' (empty query string)";
            } else {
                String lineText = query.lines().toList().get(line - 1);
                String text = lineText.substring(0, charPositionInLine) + "*" + lineText.substring(charPositionInLine);
                errorText += "'" + text + "'";
            }
        } else if (e instanceof InputMismatchException) {
            errorText += message.substring(0, message.length() - 1)
                .replace(" expecting {", ", expecting one of the following tokens: ");
        } else {
            errorText += message;
        }
        return errorText;
    }

}
