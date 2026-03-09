package io.micronaut.serde.xml;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import org.jspecify.annotations.NonNull;
import tools.jackson.dataformat.xml.XmlWriteFeature;
import tools.jackson.dataformat.xml.ser.ToXmlGenerator;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Optional;

/**
 * XML implementation of the {@link Encoder}.
 *
 * @author Mousrij Hamza
 */
public final class XmlGeneratorEncoder extends LimitingStream implements Encoder {

    private final ToXmlGenerator generator;
    private final XmlGeneratorEncoder parent;
    private final boolean isArray;

    private String currentKey;
    private int currentIndex;
    private final Deque<ArrayContext> arrayContext;

    public XmlGeneratorEncoder(ToXmlGenerator generator, @NonNull RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.generator = generator;
        this.parent = null;
        this.isArray = false;
        this.arrayContext = new ArrayDeque<>();
    }

    public XmlGeneratorEncoder(XmlGeneratorEncoder parent, @NonNull RemainingLimits remainingLimits, boolean isArray, Deque<ArrayContext> arrayContext) {
        super(remainingLimits);
        this.generator = parent.generator;
        this.parent = parent;
        this.isArray = isArray;
        this.arrayContext = arrayContext;
    }

    private void postEncodeValue() {
        currentIndex++;
    }

    private static boolean isGenericPlaceholder(String name) {
        // Single-letter uppercase names like "E", "T", "K", "V" are type variable names
        return name.length() <= 2 && name.equals(name.toUpperCase(Locale.ROOT));
    }

    @Override
    public @NonNull Encoder encodeArray(@NonNull Argument<?> type) throws IOException {
        ArrayContext parentCtx = this.arrayContext.peek();

        // Resolve logical XML name: generic placeholder like "E",
        String logicalName;
        if (parentCtx != null && isGenericPlaceholder(type.getName())) {
            logicalName = parentCtx.itemName().getLocalPart();
        } else {
            logicalName = type.getName(); // type : "List<List<SomeObject E> E> vals" ==> vals
        }
        QName qname = new QName(XMLConstants.NULL_NS_URI, logicalName);

        if (parentCtx != null) {
            // Opening a nested array: open wrapper element
            QName wn = (currentIndex == 0) ? parentCtx.itemName() : null;
            generator.startWrappedValue(wn, parentCtx.itemName());
        }

        this.arrayContext.push(new ArrayContext(qname, qname, type));
        generator.writeStartArray();
        return new XmlGeneratorEncoder(this, childLimits(), true, this.arrayContext);
    }

    @Override
    public @NonNull Encoder encodeObject(@NonNull Argument<?> type) throws IOException {
        boolean insideArray = !this.arrayContext.isEmpty();
        if (insideArray) {
            ArrayContext ctx = this.arrayContext.peek();
            QName wrapperName = (currentIndex == 0) ? ctx.wrapperName() : null;
            generator.startWrappedValue(wrapperName, ctx.itemName());
        }
        generator.writeStartObject();
        Deque<ArrayContext> contexts = new ArrayDeque<>();
        XmlGeneratorEncoder child = new XmlGeneratorEncoder(this, childLimits(), false, contexts);
        return child;
    }

    @Override
    public void finishStructure() throws IOException {
        Optional.ofNullable(parent).orElseThrow(
            () -> new IllegalStateException("Not in a structure"));
        try {
            if (isArray) {
                ArrayContext ctx = this.arrayContext.peek();
                if (ctx != null) {
                    // Close the wrapper element that startWrappedValue opened
                    generator.finishWrappedValue(ctx.wrapperName(), ctx.itemName());
                    this.arrayContext.pop(); // pop only this level
                }
                generator.writeEndArray();
            } else {
                generator.writeEndObject();
            }
        } catch (Exception e) {
            throw new IOException("Failed to finish structure", e);
        }
        parent.postEncodeValue();
    }

    @Override
    public void encodeKey(@NonNull String key) throws IOException {
        this.currentKey = key;
        generator.writeName(key);
    }

    // XmlGeneratorEncoder.java — encodeString
    @Override
    public void encodeString(@NonNull String value) throws IOException {
        ArrayContext peeked = this.arrayContext.peek();
        // Start a wrapper element for a nested string in collections
        if (peeked != null && peeked.itemName() != null) {

            // Pass null for subsequent items so Jackson reuses the open wrapper.
            QName wrapperName = (currentIndex == 0) ? peeked.itemName() : null;
            QName wrappedName = peeked.itemName();
            // Convention wrapped name same as wrapper name
            generator.startWrappedValue(
                wrapperName,
                wrappedName
            );
        }
        generator.writeString(value);
        postEncodeValue();
    }

    @Override
    public void close() throws IOException {
        Encoder.super.close();
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        generator.writeBoolean(value);
        postEncodeValue();
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        generator.writeNumber(value);
        postEncodeValue();
    }

    @Override
    public void encodeShort(short value) throws IOException {
        generator.writeNumber(value);
        postEncodeValue();
    }

    @Override
    public void encodeChar(char value) throws IOException {
        generator.writeNumber(value);
        postEncodeValue();
    }

    @Override
    public void encodeInt(int value) throws IOException {
        generator.writeNumber(value);
        postEncodeValue();
    }

    @Override
    public void encodeLong(long value) throws IOException {
        generator.writeNumber(value);
        postEncodeValue();
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        generator.writeNumber(value);
        postEncodeValue();
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        generator.writeNumber(value);
        postEncodeValue();
    }

    @Override
    public void encodeBigInteger(@NonNull BigInteger value) throws IOException {
        generator.writeNumber(value);
        postEncodeValue();
    }

    @Override
    public void encodeBigDecimal(@NonNull BigDecimal value) throws IOException {
        generator.writeNumber(value);
        postEncodeValue();
    }

    @Override
    public void encodeNull() throws IOException {
        generator.configure(XmlWriteFeature.WRITE_NULLS_AS_XSI_NIL, false);
        ArrayContext ctx = this.arrayContext.peek();
        if (ctx != null) {
            // Inside an array: open the wrapper element for this null item so that
            // finishStructure's finishWrappedValue has a matching open element.
            QName wn = (currentIndex == 0) ? ctx.itemName() : null;
            generator.startWrappedValue(wn, ctx.itemName());
        }
        generator.writeNull();
        if (ctx != null) {
            generator.finishWrappedValue(null, ctx.itemName());
        }
        postEncodeValue();
    }

    @Override
    public @NonNull String currentPath() {
        StringBuilder builder = new StringBuilder();
        XmlGeneratorEncoder enc = this;
        while (enc != null) {
            if (enc != this) {
                builder.insert(0, "->");
            }
            if (enc.currentKey == null) {
                if (enc.parent != null) {
                    builder.insert(0, enc.currentIndex);
                }
            } else {
                builder.insert(0, enc.currentKey);
            }
            enc = enc.parent;
        }
        return builder.toString();
    }

    public ToXmlGenerator getGenerator() {
        return generator;
    }

    public void setNextIsAttribute(boolean isAttribute) {
        generator.setNextIsAttribute(isAttribute);
    }

    public void setNextIsCData(boolean isCData) {
        generator.setNextIsCData(isCData);
    }

    public void setNextName(QName name) {
        generator.setNextName(name);
    }

    private record ArrayContext(
        QName wrapperName,    // passed as first arg to startWrappedValue / finishWrappedValue
        QName itemName,       // passed as second arg (per-element tag)
        Argument<?> elementType
    ) {

    }
}
