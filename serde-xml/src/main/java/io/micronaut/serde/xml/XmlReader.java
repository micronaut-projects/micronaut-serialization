package io.micronaut.serde.xml;

import io.micronaut.serde.Decoder;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.AbstractDecoderPerStructureStreamDecoder;
import io.micronaut.serde.support.AbstractStreamDecoder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;

public class XmlReader extends AbstractDecoderPerStructureStreamDecoder {

    XMLEvent event;
    XMLStreamReader reader;
    private TokenType currentToken;
    private String currentKey;

    private final Deque<KeysFrame> keysContext = new ArrayDeque<>();


    // every Start Element is considered as Key ~ StartElement

    private XmlReader(@NonNull AbstractDecoderPerStructureStreamDecoder parent, @NonNull RemainingLimits remainingLimits) {
        super(parent, remainingLimits);
    }

    private XmlReader(@NonNull AbstractDecoderPerStructureStreamDecoder parent, @NonNull RemainingLimits remainingLimits, XMLStreamReader reader) {
        super(parent, remainingLimits);
        this.reader = reader;
    }

    public XmlReader(@NonNull RemainingLimits remainingLimits, XMLStreamReader reader) {
        super(remainingLimits);
        this.reader = reader;

    }

    @Override
    protected AbstractStreamDecoder createChildDecoder() throws SerdeException {
        return new XmlReader(this, childLimits(), reader);
    }

    @Override
    protected void backFromChild(AbstractStreamDecoder child) throws IOException {
        super.backFromChild(child);
    }



    @Override
    protected AbstractStreamDecoder decodeObject0(TokenType currentToken) throws IOException {
        // change token to startObject
        System.out.println("xml reade is created");
        return super.decodeObject0(currentToken);
    }

    @Override
    protected AbstractStreamDecoder decodeArray0(TokenType currentToken) throws IOException {
        // change token to StartArray

        return super.decodeArray0(currentToken);
    }


    @Override
    protected TokenType currentToken() {
        if (getEventTypeString(reader.getEventType()).equals("START_DOCUMENT")) {
            return TokenType.START_OBJECT;
        } else if (getEventTypeString(reader.getEventType()).equals("START_ELEMENT")) {
            keysContext.push(new KeysFrame());
            return TokenType.KEY;
        }
        return null;
    }
    @Override
    protected void nextToken() throws IOException {

        //we only care about Key tags, skip root tag
        try {

            reader.next(); //

        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }

    }

    private static String getEventTypeString(int eventType) {
        return switch (eventType) {
            case XMLStreamConstants.START_ELEMENT -> "START_ELEMENT";
            case XMLStreamConstants.END_ELEMENT -> "END_ELEMENT";
            case XMLStreamConstants.PROCESSING_INSTRUCTION -> "PROCESSING_INSTRUCTION";
            case XMLStreamConstants.CHARACTERS -> "CHARACTERS";
            case XMLStreamConstants.COMMENT -> "COMMENT";
            case XMLStreamConstants.START_DOCUMENT -> "START_DOCUMENT";
            case XMLStreamConstants.END_DOCUMENT -> "END_DOCUMENT";
            case XMLStreamConstants.ENTITY_REFERENCE -> "ENTITY_REFERENCE";
            case XMLStreamConstants.ATTRIBUTE -> "ATTRIBUTE";
            case XMLStreamConstants.DTD -> "DTD";
            case XMLStreamConstants.CDATA -> "CDATA";
            case XMLStreamConstants.SPACE -> "SPACE";
            default -> "UNKNOWN_EVENT_TYPE: " + eventType;
        };
    }

    @Override
    protected String getCurrentKey() throws IOException {
        return reader.getLocalName();
    }

    @Override
    protected String coerceScalarToString(TokenType currentToken) throws IOException {
        return "";
    }

    @Override
    protected String getString() throws IOException {
        try {
            var elementText = reader.getElementText();
            int next = reader.next(); // </end>
            System.out.println("====================== " + elementText + " =====================");
            if (!getEventTypeString(next).equals("END_ELEMENT")) {
                throw new IllegalStateException("Expected END_ELEMENT, but got " + getEventTypeString(next));
            }
            return elementText;
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean getBoolean() throws IOException {
        return Boolean.parseBoolean(getString());
    }

    @Override
    protected long getLong() throws IOException {
        return Long.parseLong(getString());
    }

    @Override
    protected double getDouble() throws IOException {
        return Double.parseDouble(getString());
    }

    @Override
    protected BigInteger getBigInteger() throws IOException {
        return BigInteger.valueOf(getLong());
    }

    @Override
    protected BigDecimal getBigDecimal() throws IOException {
        return new BigDecimal(getString());
    }

    @Override
    protected Number getBestNumber() throws IOException {
        return getBigDecimal();
    }

    @Override
    protected void skipChildren() throws IOException {

    }

    @Override
    public @NonNull IOException createDeserializationException(@NonNull String message, @Nullable Object invalidValue) {
        return null;
    }


    static class Context {

    }

    static class ObjectFrame extends Context{

    }
    static class ArrayFrame extends Context{

    }
    static class KeysFrame extends Context{

    }



}
