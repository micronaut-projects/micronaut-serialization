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
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

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
                var lastPropertyKey = propertyStack.peekLast();
                var lastProperty = lastPropertyKey.getKey();
                //@jackson wrapping
                if (lastPropertyKey instanceof KeyFrame kf && kf.arrayWrappingKey != null) {
                    lastProperty = kf.arrayWrappingKey;
                    xmlWriter.writeStartElement(lastProperty);
                    propertyStack.addLast(new ArrayFrame(kf.getKey()));
                    System.out.println("encoding Array :::> " + propertyStack.toString());
                    return this;
                }
                //wrapping
                    xmlWriter.writeStartElement(lastProperty);
                propertyStack.addLast(new ArrayFrame(lastProperty)); // [O(key), K2(nameKey_1, false), A(nameKey_1), ]
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

            if (propertyStack.peekLast() instanceof KeyFrame kf || propertyStack.peekLast() instanceof ArrayFrame) {
                Deque<ContextProperties> innerPropertyStack = new ArrayDeque<>();
                //[O, K, A, O]
                if (propertyStack.peekLast() instanceof KeyFrame kf) { // [O, K, ] ==> inner = [K] ==> inner =[K, O]
                    xmlWriter.writeStartElement(kf.getKey());
                    innerPropertyStack.addLast(propertyStack.peekLast());
                }
                innerPropertyStack.addLast(new ObjectFrame(name));
                xmlWriter.writeStartElement(name);

                return new XmlGenerator(xmlWriter, innerPropertyStack);
            }

            propertyStack.addLast(new ObjectFrame(name));   // << [ObjectFrame(name)]
            xmlWriter.writeStartElement(name);  // <CustomBean>

        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public void finishStructure() throws IOException {
        try {

            var lastProperty = propertyStack.peekLast();
            if (propertyStack.peekFirst() instanceof KeyFrame kf) {
                xmlWriter.writeEndElement();
            }
            switch (lastProperty) {
                case KeyFrame kf -> {
                    xmlWriter.writeEndElement();   // [ObjectFrame(name), KeyFrame3(name, false)] && <CustomBean><A1>a1</A1><C1><C1>c1</c1><C1>c2</c1><C1><C3>c3</c3>
                    propertyStack.clear();

                }
                case ObjectFrame of -> {
                    xmlWriter.writeEndElement();
                    propertyStack.clear();
                }
                case ArrayFrame of -> {
                    if (of.getKey() != null && of.getKey().isEmpty()) {
                        propertyStack.removeLast();
                        return;
                    }
                    if (propertyStack.size() == 1 && propertyStack.peekLast() instanceof ArrayFrame af) { // [A(ArrayList)]
                        xmlWriter.writeEndElement();
                        return;
                    }
                    propertyStack.removeLast();  // // [o, k(name2, false), A(name2)]
                    xmlWriter.writeEndElement();  // [o, k(name2, false)]
                    assert propertyStack.peekLast() instanceof KeyFrame : "This should be a keyFrame" + propertyStack.toString();
                    KeyFrame last = (KeyFrame) propertyStack.peekLast();
                    last.setConsumed(true);   //[O, K(,,false), ##K]

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
                propertyStack.addLast(new ObjectFrame(key, true));  // @JsonRoot("dsq") [ObjectFrame("dsq")]
                xmlWriter.writeStartElement(key);
                return;
            }

            //simpleObjectSerializer  --- iteration on the loop
            if (!propertyStack.isEmpty() && propertyStack.getLast() instanceof KeyFrame of && of.consumed) {
                propertyStack.removeLast(); // [ObjectFrame(name)]
            } else if (propertyStack.peekLast() instanceof KeyFrame of && !of.consumed) { // [O, K(name, false, null)] && add wrapping tag, come from the xmlWrapperSerde
                of.setArrayWrappingKey(key);
                return;
            }

            propertyStack.addLast(new KeyFrame(key, false));  // [ObjectFrame(name), KeyFrame2(name, false)]

        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeScalar(String data) {
        try {
            var lastProperty = propertyStack.getLast();  // [ObjectFrame(name), K1(name1, false)] || [ObjectFrame(name), K2(name2, false), A(name2)] || [A(ArrayList)]
            switch (lastProperty) {
                case KeyFrame kf -> {
                    xmlWriter.writeStartElement(kf.getKey());     // <CustomBean><A1>a1</A1>
                        xmlWriter.writeCharacters(data);    //. <CustomBean><A1>a1</A1><C1><C1>c1</c1><C1>c2</c1><C1><C3>c3
                    xmlWriter.writeEndElement();
                    kf.setConsumed(true);
                }
                case ArrayFrame af -> {
                    String itemName = null;
                    String iterableKey = af.getIterableKey();
                    Optional<String> maybeIterableKey = Optional.ofNullable(iterableKey).filter(s -> !s.isEmpty());
                    if (maybeIterableKey.isPresent()) {
                        itemName = maybeIterableKey.get();
                    } else {
                        itemName = af.getKey();
                    }
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
        if (!(lastProperty instanceof KeyFrame keyFrame) || keyFrame.consumed) {
            throw new IllegalStateException("Expected a pending key before starting an inline array, but found: " + lastProperty);
        }

        propertyStack.removeLast(); // remove the property key, so no <values> or <kilo>
        propertyStack.addLast(new ArrayFrame("")); // sentinel for inline/no-wrapper array
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
        writeScalar(new String(Byte.toString(value).getBytes(), StandardCharsets.UTF_8));
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
            xmlWriter.writeEndElement();
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
            xmlWriter.writeAttribute(keyFrame.getKey(), value);  // [O, K1(name, false)]
            keyFrame.setConsumed(true);
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
            xmlWriter.writeStartElement(lastKey.getKey());
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        // closing the tag is the custom wrapper serializer responsibility
    }

    abstract static class ContextProperties {
        private String key;

        public ContextProperties(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return "contextProperties{" +
                "key='" + key + '\'' +
                '}';
        }
    }

    private static class ObjectFrame extends ContextProperties {

        @Nullable
        Boolean ignore;

        @Nullable
        Boolean rootName;

        public ObjectFrame(String key) {
            super(key);
            this.rootName = null;
        }

        public ObjectFrame(String key, @Nullable Boolean rootName) {
            super(key);
            this.rootName = rootName;
        }

        public void setIgnore(@Nullable Boolean ignore) {
            this.ignore = ignore;
        }

        @Override
        public String toString() {
            return "ObjectFrame{" +
                "key='" + getKey() + '\'' +
                '}';
        }
    }

    private static class KeyFrame extends ContextProperties {
        boolean consumed;
        @Nullable
        String arrayWrappingKey;

        @Nullable
        Boolean ObjectWrappingKey;

        public KeyFrame(String key, Boolean consumed) {

            super(key);
            this.consumed = consumed;
        }

        public boolean isConsumed() {
            return consumed;
        }

        public void setConsumed(boolean consumed) {
            this.consumed = consumed;
        }

        public String getArrayWrappingKey() {
            return arrayWrappingKey;
        }

        public void setArrayWrappingKey(String arrayWrappingKey) {
            this.arrayWrappingKey = arrayWrappingKey;
        }

        public void setObjectWrappingKey(@Nullable Boolean objectWrappingKey) {
            ObjectWrappingKey = objectWrappingKey;
        }

        @Override
        public String toString() {
            return "KeyFrame{" +
                "arrayWrappingKey='" + arrayWrappingKey + '\'' +
                ", consumed=" + consumed +
                ", key='" + this.getKey() + '\'' +
                '}';
        }
    }

    private static class ArrayFrame extends ContextProperties {

        @Nullable
        String IterableKey;

        @Nullable
        String wrappingKey;

        public ArrayFrame(String key) {
            super(key);
            this.IterableKey = null;
        }

        public ArrayFrame(String key, @Nullable String iterableKey) {
            super(key);
            IterableKey = iterableKey;
        }

        @Override
        public String toString() {
            return "ArrayFrame{" +
                "key='" + getKey() + '\'' +
                '}';
        }

        public @Nullable String getIterableKey() {
            return IterableKey;
        }

        public void setIterableKey(@Nullable String iterableKey) {
            IterableKey = iterableKey;
        }

        public @Nullable String getWrappingKey() {
            return wrappingKey;
        }

        public void setWrappingKey(@Nullable String wrappingKey) {
            this.wrappingKey = wrappingKey;
        }
    }
}
