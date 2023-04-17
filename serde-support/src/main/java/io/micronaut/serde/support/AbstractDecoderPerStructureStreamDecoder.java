/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.serde.support;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.io.IOException;

/**
 * Abstract variation of {@link AbstractStreamDecoder} that uses separate decoders for structures like an array or an object.
 */
public abstract class AbstractDecoderPerStructureStreamDecoder extends AbstractStreamDecoder {

    @Nullable
    AbstractDecoderPerStructureStreamDecoder parent;

    private AbstractStreamDecoder child = null;

    /**
     * Child constructor. Should inherit the parser from the parent.
     * @param parent The parent decoder.
     */
    protected AbstractDecoderPerStructureStreamDecoder(@NonNull AbstractDecoderPerStructureStreamDecoder parent) {
        this.parent = parent;
    }

    /**
     * Root constructor.
     */
    protected AbstractDecoderPerStructureStreamDecoder() {
    }

    /**
     * Create a new child decoder using {@link AbstractDecoderPerStructureStreamDecoder#AbstractDecoderPerStructureStreamDecoder(AbstractDecoderPerStructureStreamDecoder)}.
     * @return The new decoder
     */
    protected abstract AbstractStreamDecoder createChildDecoder();

    /**
     * Create a child decoder, and transfer control of the stream to it (see {@link #checkChild()}).
     *
     * @return The new child decoder
     */
    AbstractStreamDecoder childDecoder() {
        return createChildDecoder();
    }

    @Override
    protected void preDecodeValue(TokenType currentToken) {
        super.preDecodeValue(currentToken);
        checkChild();
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        checkChild();
        super.finishStructure(consumeLeftElements);
        if (parent != null) {
            transferControlToParent();
        }
    }

    @Override
    public boolean hasNextArrayValue() {
        checkChild();
        return super.hasNextArrayValue();
    }

    public final String decodeKey() throws IOException {
        checkChild();
        return super.decodeKey();
    }

    @Override
    protected AbstractStreamDecoder decodeArray0(TokenType currentToken) throws IOException {
        super.decodeArray0(currentToken);
        child = childDecoder();
        return child;
    }

    @Override
    protected AbstractStreamDecoder decodeObject0(TokenType currentToken) throws IOException {
        super.decodeObject0(currentToken);
        child = childDecoder();
        return child;
    }

    private void checkChild() {
        if (child != null) {
            throw new IllegalStateException("There is still an unfinished child parser");
        }
        if (parent != null && parent.child != this) {
            throw new IllegalStateException("This child parser has already completed");
        }
    }

    /**
     * Transfer control back to the parent (for {@link #checkChild()}), and call {@link #backFromChild}.
     */
    void transferControlToParent() throws IOException {
        parent.child = null;
        parent.backFromChild(this);
    }

    /**
     * Called when the old child has finished processing and returns control of the stream to the parent. Current token
     * is assumed to be {@link TokenType#END_ARRAY} or {@link TokenType#END_OBJECT}. However we {@link #currentToken()}
     * may hold an outdated value until {@link #nextToken()} is called, which this method does.
     *
     * @param child The now-invalid child decoder.
     * @throws java.io.IOException if an unrecoverable error occurs
     */
    protected void backFromChild(AbstractStreamDecoder child) throws IOException {
        nextToken();
    }

}
