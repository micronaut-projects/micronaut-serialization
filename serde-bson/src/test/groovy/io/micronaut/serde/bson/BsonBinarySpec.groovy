package io.micronaut.serde.bson

import io.micronaut.core.type.Argument
import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.BsonDocument
import org.bson.codecs.BsonDocumentCodec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.io.BasicOutputBuffer
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings

import java.nio.ByteBuffer

trait BsonBinarySpec {

    abstract BsonBinaryMapper getBsonBinaryMapper()

    String encodeAsBinaryDecodeJson(Object obj) {
        def data = getBsonBinaryMapper().writeValueAsBytes(obj)
        return readByteArrayAsJson(data)
    }

    def <T> T encodeAsBinaryDecodeAsObject(BsonDocument ob, Class<T> type) {
        def bytes = writeToByteArray(ob)
        return bsonBinaryMapper.readValue(bytes, Argument.of(type))
    }

    def <T> T encodeAsBinaryDecodeAsObject(T obj) {
        def bytes = bsonBinaryMapper.writeValueAsBytes(obj)
        return bsonBinaryMapper.readValue(bytes, Argument.of(obj.getClass()))
    }

    static byte[] writeToByteArray(BsonDocument document) {
        BasicOutputBuffer buffer = new BasicOutputBuffer()
        BsonBinaryWriter writer = new BsonBinaryWriter(buffer)
        new BsonDocumentCodec().encode(writer, document, EncoderContext.builder().isEncodingCollectibleDocument(true).build())
        return buffer.toByteArray()
    }

    static String readByteArrayAsJson(byte[] bytes) {
        BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(bytes))
        def document = new BsonDocumentCodec().decode(reader, DecoderContext.builder().build())
        return document.toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).indentCharacters("").build())
    }

}