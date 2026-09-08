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
package io.micronaut.serde.jackson.compiletime;

import io.micronaut.serde.annotation.SerdeableGenerated;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Record counterpart of {@link SourceGenInclusionShapeBean}.
 *
 * @param text         A string component
 * @param boxedFlag    A boxed boolean component
 * @param boxedLetter  A boxed character component
 * @param boxedByte    A boxed byte component
 * @param boxedShort   A boxed short component
 * @param boxedInt     A boxed int component
 * @param boxedLong    A boxed long component
 * @param boxedFloat   A boxed float component
 * @param boxedDouble  A boxed double component
 * @param bigInteger   A big integer component
 * @param bigDecimal   A big decimal component
 * @param flag         A primitive boolean component
 * @param letter       A primitive char component
 * @param count        A primitive int component
 * @param id           A primitive long component
 * @param ratio        A primitive float component
 * @param score        A primitive double component
 * @param tags         A list component written through a property serializer
 * @param attributes   A map component written through a property serializer
 */
@SerdeableGenerated
public record SourceGenInclusionShapeRecord(
    String text,
    Boolean boxedFlag,
    Character boxedLetter,
    Byte boxedByte,
    Short boxedShort,
    Integer boxedInt,
    Long boxedLong,
    Float boxedFloat,
    Double boxedDouble,
    BigInteger bigInteger,
    BigDecimal bigDecimal,
    boolean flag,
    char letter,
    int count,
    long id,
    float ratio,
    double score,
    List<String> tags,
    Map<String, String> attributes
) {
}
