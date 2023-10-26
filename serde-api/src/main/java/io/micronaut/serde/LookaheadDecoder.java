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
package io.micronaut.serde;

import io.micronaut.core.annotation.Experimental;

import java.io.IOException;

/**
 * A variation of {@link Decoder} that allows to replay the decoder.
 *
 * @since 2.3
 */
@Experimental
public interface LookaheadDecoder extends Decoder {

    /**
     * Replay the lookahead and continue with the original decoder.
     * @return The replay decoder
     */
    Decoder replay() throws IOException;

}
