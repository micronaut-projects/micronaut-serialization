/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.bson.custom;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;
import org.bson.BsonBinary;
import org.bson.BsonDbPointer;
import org.bson.BsonDocument;
import org.bson.BsonRegularExpression;
import org.bson.BsonTimestamp;
import org.bson.codecs.BsonBinaryCodec;
import org.bson.codecs.BsonDBPointerCodec;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.BsonRegularExpressionCodec;
import org.bson.codecs.BsonTimestampCodec;
import org.bson.codecs.Decimal128Codec;
import org.bson.codecs.ObjectIdCodec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.util.Optional;

@Factory
@Internal
final class CustomBsonSerdes {

    @Singleton
    @NonNull
    @Order(10000) // Avoid being chosen for Map
    CodecBsonDecoder<BsonDocument> bsonDecoder(Optional<CodecRegistry> codecRegistry) {
        return codecRegistry.map(registry -> new CodecBsonDecoder<>(new BsonDocumentCodec(registry)))
                .orElseGet(() -> new CodecBsonDecoder<>(new BsonDocumentCodec()));
    }

    @Singleton
    @NonNull
    CodecBsonDecoder<ObjectId> objectId() {
        return new CodecBsonDecoder<>(new ObjectIdCodec());
    }

    @Singleton
    @NonNull
    CodecBsonDecoder<Decimal128> decimal128() {
        return new CodecBsonDecoder<>(new Decimal128Codec());
    }

    @Singleton
    @NonNull
    CodecBsonDecoder<BsonRegularExpression> bsonRegularExpression() {
        return new CodecBsonDecoder<>(new BsonRegularExpressionCodec());
    }

    @Singleton
    @NonNull
    CodecBsonDecoder<BsonBinary> bsonBinary() {
        return new CodecBsonDecoder<>(new BsonBinaryCodec());
    }

    @Singleton
    @NonNull
    CodecBsonDecoder<BsonDbPointer> bsonDbPointer() {
        return new CodecBsonDecoder<>(new BsonDBPointerCodec());
    }

    @Singleton
    @NonNull
    CodecBsonDecoder<BsonTimestamp> bsonTimestamp() {
        return new CodecBsonDecoder<>(new BsonTimestampCodec());
    }

}
