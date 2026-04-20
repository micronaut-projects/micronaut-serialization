package io.micronaut.serde.xml.serde;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.xml.XmlGenerator;
import java.io.IOException;

public class XmlPropertySerde extends XmlSerde {
    @Override
    protected void doSerialize(XmlGenerator encoder, EncoderContext context, Object value, Argument key) throws IOException {
        if (value == null) {
            encoder.encodeNull();
        } else {
            encoder.writeAttributeForCurrentKey(String.valueOf(value));
        }
    }
}
