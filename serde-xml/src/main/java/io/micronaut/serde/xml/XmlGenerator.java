/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.xml;

import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;

/**
 *
 */
public final class XmlGenerator implements Encoder {

    private final XMLStreamWriter xmlWriter;
    private final Deque<ContextProperties> propertyStack = new ArrayDeque<>();
    private Boolean rootMapper;

    public XmlGenerator(XMLStreamWriter xmlWriter) {
        this.xmlWriter = xmlWriter;
        this.rootMapper = false;
    }

    private XmlGenerator(XMLStreamWriter xmlWriter, Boolean rootMapper) {
        this.xmlWriter = xmlWriter;
        this.rootMapper = rootMapper;
    }

    private XmlGenerator(XMLStreamWriter xmlWriter, Deque<ContextProperties> propertyStack) {
        this.xmlWriter = xmlWriter;
        this.propertyStack.addAll(propertyStack);
        this.rootMapper = false;
    }

    @Override
    public @NonNull Encoder encodeArray(@NonNull Argument<?> type) throws IOException {
        // [O(),K ]
        try {
            if (!propertyStack.isEmpty()) {
                ContextProperties lastPropertyKey = propertyStack.peekLast();
                String lastProperty = lastPropertyKey.key();
                //@jackson wrapping
                if (lastPropertyKey instanceof KeyFrame kf && kf.arrayWrappingKey() != null) {
                    lastProperty = kf.arrayWrappingKey();
                    xmlWriter.writeStartElement(lastProperty);
                    propertyStack.addLast(new ArrayFrame(kf.key(), null));
                    return this;
                }
                //wrapping
                xmlWriter.writeStartElement(lastProperty);
                propertyStack.addLast(new ArrayFrame(lastProperty, null)); // [O(key), K2(nameKey_1, false), A(nameKey_1), ]
                return this;
            } else  {
                // IterableValueSerializer
                String collectionName = NameUtils.camelCase(type.getName(), false);
                ArrayFrame arrayFrame = new ArrayFrame(collectionName, "item");
                propertyStack.addLast(arrayFrame);  // [A(name), ..., ]

                // <ArrayList>  ... </ArrayList>
                xmlWriter.writeStartElement(collectionName);
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public @NonNull Encoder encodeObject(@NonNull Argument<?> type) throws IOException {

        String name = type.getSimpleName();
        // for the root name with @JsonRootName only
        if (type.equals(Argument.OBJECT_ARGUMENT)) {
            Boolean rootMapper = true;
            return new XmlGenerator(xmlWriter, rootMapper) ; // []
        }
        try {
            if (rootMapper) {
                rootMapper = false;
                return this;
            }

            ContextProperties last = propertyStack.peekLast();
            if (last instanceof KeyFrame || last instanceof ArrayFrame) {
                Deque<ContextProperties> innerPropertyStack = new ArrayDeque<>(8);
                //[O, K, A, O]
                if (last instanceof KeyFrame kf) { // [O, K, ] ==> inner = [K] ==> inner =[K, O]
                    // Replace-on-update: set objectWrappingKey = FALSE
                    KeyFrame updated = new KeyFrame(kf.key(), kf.consumed(), kf.arrayWrappingKey(), Boolean.FALSE);
                    propertyStack.removeLast();
                    propertyStack.addLast(updated);
                    innerPropertyStack.addLast(updated);
                    innerPropertyStack.addLast(new ObjectFrame(null, null));
                    return new XmlGenerator(xmlWriter, innerPropertyStack);
                }
                if (last instanceof ArrayFrame af) {
                    String iterableKey = af.iterableKey();
                    String afKey = af.key();
                    String itemName = (iterableKey != null && !iterableKey.isEmpty()) ? iterableKey
                        : (afKey != null && !afKey.isEmpty()) ? afKey
                        : name;
                    innerPropertyStack.addLast(new ObjectFrame(itemName, null));
                    xmlWriter.writeStartElement(itemName);
                }

                return new XmlGenerator(xmlWriter, innerPropertyStack);
            }

            propertyStack.addLast(new ObjectFrame(name, null));   // << [ObjectFrame(name)]
            xmlWriter.writeStartElement(name);  // <CustomBean>

        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public void finishStructure() throws IOException {
        try {

            ContextProperties lastProperty = propertyStack.peekLast();
            switch (lastProperty) {
                case KeyFrame kf -> {
                    xmlWriter.writeEndElement();   // [ObjectFrame(name), KeyFrame3(name, false)] && <CustomBean><A1>a1</A1><C1><C1>c1</c1><C1>c2</c1><C1><C3>c3</c3>
                    propertyStack.clear();

                }
                case ObjectFrame of -> {
                    if (of.key() != null) {
                        xmlWriter.writeEndElement();
                    }
                    if (propertyStack.peekFirst() instanceof KeyFrame kf) {
                        if (of.key() == null) {
                            if (Boolean.TRUE.equals(kf.objectWrappingKey())) {
                                xmlWriter.writeEndElement();
                            } else {
                                xmlWriter.writeEmptyElement(kf.key());
                            }
                        }
                        // Replace-on-update: mark consumed = true on the first KeyFrame
                        // (single-element stack mutation: peekFirst == peekLast in this branch
                        //  since after writing end element this is the lone leftover frame).
                        // We clear() right after, so no need to actually rewrite the frame.
                    }
                    propertyStack.clear();
                }
                case ArrayFrame af -> {
                    if (af.key() != null && af.key().isEmpty()) {
                        propertyStack.removeLast();
                        return;
                    }
                    if (propertyStack.size() == 1 && propertyStack.peekLast() instanceof ArrayFrame) { // [A(ArrayList)]
                        xmlWriter.writeEndElement();
                        return;
                    }
                    propertyStack.removeLast();  // // [o, k(name2, false), A(name2)]
                    xmlWriter.writeEndElement();  // [o, k(name2, false)]
                    assert propertyStack.peekLast() instanceof KeyFrame : "Expected KeyFrame, got: " + propertyStack.peekLast();
                    KeyFrame last = (KeyFrame) propertyStack.removeLast();
                    propertyStack.addLast(new KeyFrame(last.key(), true, last.arrayWrappingKey(), last.objectWrappingKey()));   //[O, K(,,false), ##K]

                } case null -> {
                    assert  propertyStack.isEmpty() : "Root name mapping";

                }
                default -> throw new IllegalStateException("Unexpected value: " + lastProperty);
            }

        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encodeKey(@NonNull String key) throws IOException {
        try {
            if (rootMapper) { // [O(name, true), ]
                propertyStack.addLast(new ObjectFrame(key, Boolean.TRUE));  // @JsonRoot("dsq") [ObjectFrame("dsq")]
                xmlWriter.writeStartElement(key);
                return;
            }

            ensurePendingObjectElementStarted();

            //simpleObjectSerializer  --- iteration on the loop
            ContextProperties last = propertyStack.peekLast();
            if (last instanceof KeyFrame of && of.consumed()) {
                propertyStack.removeLast(); // [ObjectFrame(name)]
            } else if (last instanceof KeyFrame of && !of.consumed()) { // [O, K(name, false, null)] && add wrapping tag, come from the xmlWrapperSerde
                // Replace-on-update: set arrayWrappingKey = key
                propertyStack.removeLast();
                propertyStack.addLast(new KeyFrame(of.key(), of.consumed(), key, of.objectWrappingKey()));
                return;
            }

            propertyStack.addLast(new KeyFrame(key, false, null, null));  // [ObjectFrame(name), KeyFrame2(name, false)]

        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private void ensurePendingObjectElementStarted() throws XMLStreamException {
        if (propertyStack.size() == 2
            && propertyStack.peekFirst() instanceof KeyFrame keyFrame
            && propertyStack.peekLast() instanceof ObjectFrame objectFrame
            && objectFrame.key() == null
            && !Boolean.TRUE.equals(keyFrame.objectWrappingKey())) {
            xmlWriter.writeStartElement(keyFrame.key());
            // Replace-on-update: set objectWrappingKey = TRUE on the first frame
            propertyStack.removeFirst();
            propertyStack.addFirst(new KeyFrame(keyFrame.key(), keyFrame.consumed(), keyFrame.arrayWrappingKey(), Boolean.TRUE));
        }
    }

    private void writeScalar(String data) {
        try {
            ContextProperties lastProperty = propertyStack.getLast();  // [ObjectFrame(name), K1(name1, false)] || [ObjectFrame(name), K2(name2, false), A(name2)] || [A(ArrayList)]
            switch (lastProperty) {
                case KeyFrame kf -> {
                    xmlWriter.writeStartElement(kf.key());     // <CustomBean><A1>a1</A1>
                    xmlWriter.writeCharacters(data);    //. <CustomBean><A1>a1</A1><C1><C1>c1</c1><C1>c2</c1><C1><C3>c3
                    xmlWriter.writeEndElement();
                    // Replace-on-update: mark consumed = true
                    propertyStack.removeLast();
                    propertyStack.addLast(new KeyFrame(kf.key(), true, kf.arrayWrappingKey(), kf.objectWrappingKey()));
                }
                case ArrayFrame af -> {
                    String iterableKey = af.iterableKey();
                    String itemName = (iterableKey != null && !iterableKey.isEmpty()) ? iterableKey : af.key();
                    xmlWriter.writeStartElement(itemName);
                    xmlWriter.writeCharacters(data);
                    xmlWriter.writeEndElement();
                    // ====<CustomBean><A1>a1</A1><C1><C1>c1</c1><C1>c2</c1>
                }
                default -> throw new IllegalStateException("Unexpected value in writeScalar(): " + lastProperty + "\t " + lastProperty.getClass().getName());
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Array Object inline to the XML generator.
     * @param type
     * @return Encoder
     */
    public @NonNull Encoder encodeInlineArray(@NonNull Argument<?> type) throws IOException {
        ContextProperties lastProperty = propertyStack.peekLast();
        if (!(lastProperty instanceof KeyFrame keyFrame) || keyFrame.consumed()) {
            throw new IllegalStateException("Expected a pending key before starting an inline array, but found: " + lastProperty);
        }

        propertyStack.removeLast(); // remove the property key, so no wrapper element is written
        propertyStack.addLast(new ArrayFrame("", keyFrame.key())); // sentinel for inline/no-wrapper array
        return this;
    }

    @Override
    public void encodeString(@NonNull String value) throws IOException {
        writeScalar(value);
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        writeScalar(Boolean.toString(value));
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        writeScalar(Byte.toString(value));
    }

    @Override
    public void encodeBinary(byte @NonNull [] data) throws IOException {
        writeScalar(Base64.getEncoder().encodeToString(data));
    }

    @Override
    public void encodeShort(short value) throws IOException {
        writeScalar(Short.toString(value));
    }

    @Override
    public void encodeChar(char value) throws IOException {
        writeScalar(Character.toString(value));
    }

    @Override
    public void encodeInt(int value) throws IOException {
        writeScalar(Integer.toString(value));
    }

    @Override
    public void encodeLong(long value) throws IOException {
        writeScalar(Long.toString(value));
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        writeScalar(Float.toString(value));
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        writeScalar(Double.toString(value));
    }

    @Override
    public void encodeBigInteger(@NonNull BigInteger value) throws IOException {
        writeScalar(String.valueOf(value));
    }

    @Override
    public void encodeBigDecimal(@NonNull BigDecimal value) throws IOException {
        writeScalar(String.valueOf(value));
    }

    @Override
    public void encodeNull() throws IOException {
        try {
            ContextProperties lastProperty = propertyStack.peekLast();
            switch (lastProperty) {
                case KeyFrame kf -> {
                    xmlWriter.writeEmptyElement(kf.key());
                    // Replace-on-update: mark consumed = true
                    propertyStack.removeLast();
                    propertyStack.addLast(new KeyFrame(kf.key(), true, kf.arrayWrappingKey(), kf.objectWrappingKey()));
                }
                case null -> xmlWriter.writeEndElement();
                default -> xmlWriter.writeEndElement();
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Gets the XML stream writer for custom SerDes.
     *
     * @return The XML stream writer.
     */
    public XMLStreamWriter getXmlWriter() {
        return xmlWriter;
    }

    /**
     * Write an XML attribute for the current pending property key.
     * @param value
     */
    public void writeAttributeForCurrentKey(String value) throws IOException {
        ContextProperties lastProperty = propertyStack.peekLast();
        if (!(lastProperty instanceof KeyFrame keyFrame)) {
            throw new IllegalStateException("Expected a pending XML key before writing an attribute, but found: " + lastProperty);
        }
        try {
            xmlWriter.writeAttribute(keyFrame.key(), value);  // [O, K1(name, false)]
            // Replace-on-update: mark consumed = true
            propertyStack.removeLast();
            propertyStack.addLast(new KeyFrame(keyFrame.key(), true, keyFrame.arrayWrappingKey(), keyFrame.objectWrappingKey()));
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
    }

    /**
     * Writes a namespaced scalar element for the current pending property key.
     *
     * @param localName the element local name
     * @param namespaceUri the namespace URI (may be null/empty for the default namespace)
     * @param value the textual value
     */
    public void writeNamespacedScalarForCurrentKey(String localName, String namespaceUri, String value) throws IOException {
        try {
            ensurePendingObjectElementStarted();
            ContextProperties last = propertyStack.peekLast();
            if (!(last instanceof KeyFrame keyFrame)) {
                throw new IllegalStateException("Expected a pending XML key before writing a namespaced element, but found: " + last);
            }
            if (namespaceUri == null || namespaceUri.isEmpty()) {
                xmlWriter.writeStartElement(localName);
            } else {
                xmlWriter.writeStartElement(namespaceUri, localName);
            }
            xmlWriter.writeCharacters(value);
            xmlWriter.writeEndElement();
            propertyStack.removeLast();
            propertyStack.addLast(new KeyFrame(keyFrame.key(), true, keyFrame.arrayWrappingKey(), keyFrame.objectWrappingKey()));
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
    }

    /**
     * Writes an XML start element for the XmlWrapper custom serializer.
     */
    public void wrapElement() {
        // must be [O, K(name, false)]
        ContextProperties lastKey = propertyStack.peekLast();
        try {
            xmlWriter.writeStartElement(lastKey.key());
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        // closing the tag is the custom wrapper serializer responsibility
    }

    sealed interface ContextProperties permits ObjectFrame, KeyFrame, ArrayFrame {
        String key();
    }

    private record ObjectFrame(String key, @Nullable Boolean rootName) implements ContextProperties {
    }

    private record KeyFrame(
        String key,
        boolean consumed,
        @Nullable String arrayWrappingKey,
        @Nullable Boolean objectWrappingKey
    ) implements ContextProperties {
    }

    private record ArrayFrame(String key, @Nullable String iterableKey) implements ContextProperties {
    }
}
