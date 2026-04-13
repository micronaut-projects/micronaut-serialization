package io.micronaut.serde.xml;

import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
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

public class XmlGenerator implements Encoder {

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
        if (!propertyStack.isEmpty()) {
            var lastProperty = propertyStack.getLast().getKey();
            var name = type.getName();
            //wrapping
            propertyStack.addLast(new ArrayFrame(lastProperty)); // [O(key), K2(nameKey_1, false), A(nameKey_1), ]
            return this;
        } else  {
            // IterableValueSerializer
            String collectionName = NameUtils.camelCase(type.getName(), false);
            ArrayFrame arrayFrame = new ArrayFrame(collectionName, "item");
            propertyStack.addLast(arrayFrame);  // [A(name), ..., ]
            try {
                // <ArrayList>  ... </ArrayList>
                xmlWriter.writeStartElement(collectionName);
            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }
            return this;
        }
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

            if (propertyStack.peekLast() != null){ // High probably is key  [O, K]
                //propertyStack.addLast(new ObjectFrame(name));   // << [ObjectFrame(name)]
                xmlWriter.writeStartElement(name);
                Deque<ContextProperties> innerPropertyStack = new ArrayDeque<>();
                propertyStack.addLast(new ObjectFrame(name));
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

            var lastProperty = propertyStack.peekLast(); // [ObjectFrame(name), KeyFrame3(name, false)] && <CustomBean><A1>a1</A1><C1><C1>c1</c1><C1>c2</c1><C1><C3>c3
            switch (lastProperty) {
                case KeyFrame kf -> {
                    //SimpleObjectSerializer
                    kf.setConsumed(true);
                    xmlWriter.writeEndElement(); // [ObjectFrame(name), KeyFrame3(name, false)] && <CustomBean><A1>a1</A1><C1><C1>c1</c1><C1>c2</c1><C1><C3>c3</c3>
                    propertyStack.removeLast(); //
                    xmlWriter.writeEndElement();
                    propertyStack.clear();

                }
                case ObjectFrame of -> {
                    xmlWriter.writeEndElement();
                    propertyStack.clear();
                }
                case ArrayFrame of -> {

                    if (propertyStack.size() == 1 && propertyStack.peekLast() instanceof ArrayFrame af) { // [A(ArrayList)]
                        xmlWriter.writeEndElement();
                    }
                    propertyStack.removeLast();  // // [o, k(name2, false), A(name2)]
                    //xmlWriter.writeEndElement();  // [o, k(name2, false)]
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

            if (rootMapper) {
                propertyStack.addLast(new ObjectFrame(key));  // @JsonRoot("dsq") [ObjectFrame("dsq")]
                xmlWriter.writeStartElement(key);
                return;
            }

            //simpleObjectSerializer  --- iteration on the loop
            if (!propertyStack.isEmpty() && propertyStack.getLast() instanceof KeyFrame of && !of.consumed) {                                                      // don't do writeEnd-element because it's already made for endArray in finish-structure
                of.setConsumed(true);    // //  [ObjectFrame(name), KeyFrame2(name, true)]
                xmlWriter.writeEndElement(); // <CustomBean><A1>a1</A1> ===> <CustomBean><A1>a1</A1><C1><C1>c1</c1><C1>c2</c1><C1>

                propertyStack.removeLast(); // [ObjectFrame(name)]
            }



            propertyStack.addLast(new KeyFrame(key, false));  // [ObjectFrame(name), KeyFrame3(name, false)]
            xmlWriter.writeStartElement(key); // new property coming from the loop    ====<CustomBean><A1>a1</A1><C1><C1>c1</c1><C1>c2</c1><C1><C3>c3

        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeScalar(String data) {
        try { //
            var lastProperty = propertyStack.getLast();  // // [ObjectFrame(name), K2(name2, false), A(name2)] || [A(ArrayList)]
            switch (lastProperty) {
                case KeyFrame kf -> {
                    xmlWriter.writeCharacters(data);    //. <CustomBean><A1>a1</A1><C1><C1>c1</c1><C1>c2</c1><C1><C3>c3
                }
                case ArrayFrame af -> {
                    String itemName= null;
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
        public ObjectFrame(String key) {
            super(key);
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
        public KeyFrame(String key,  Boolean consumed) {
            super(key);
            this.consumed = consumed;
        }

        public boolean isConsumed() {
            return consumed;
        }

        public void setConsumed(boolean consumed) {
            this.consumed = consumed;
        }

        @Override
        public String toString() {
            return "KeyFrame{" +
                "consumed=" + consumed + "; \t key : " + getKey() +
                '}';
        }
    }

    private static class ArrayFrame extends ContextProperties{

        @Nullable
        String IterableKey;


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
    }
}
