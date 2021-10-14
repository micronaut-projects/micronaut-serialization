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
package io.micronaut.json.generator.symbol.bean;

import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.Internal;
import io.micronaut.json.generator.symbol.GeneratorContext;

import java.util.*;

/**
 * {@link java.util.BitSet} equivalent that is inlined as multiple generated long variables.
 */
@Internal
class InlineBitSet<T> {
    private final List<String> maskVariables;
    private final Map<T, Integer> offsets;

    InlineBitSet(GeneratorContext context, Collection<T> values, String nameHint) {
        offsets = new HashMap<>();
        int offset = 0;
        for (T property : values) {
            offsets.put(property, offset);
            offset++;
        }

        // generate one mask for every 64 variables
        maskVariables = new ArrayList<>();
        for (int i = 0; i < offset; i += 64) {
            maskVariables.add(context.newLocalVariable(nameHint));
        }
    }

    void emitMaskDeclarations(CodeBlock.Builder output, boolean initialValue) {
        if (initialValue) {
            BitSet all = new BitSet();
            all.set(0, offsets.size());
            long[] initialValues = all.toLongArray();
            for (int i = 0; i < maskVariables.size(); i++) {
                output.addStatement("long $N = $L", maskVariables.get(i), toHexLiteral(initialValues[i]));
            }
        } else {
            emitMaskDeclarations(output);
        }
    }

    void emitMaskDeclarations(CodeBlock.Builder output) {
        for (String maskVariable : maskVariables) {
            output.addStatement("long $N = 0", maskVariable);
        }
    }

    private String maskVariable(int offset) {
        return maskVariables.get(offset / 64);
    }

    private String mask(int offset) {
        // shift does an implicit modulo
        long value = 1L << offset;
        return toHexLiteral(value);
    }

    private String toHexLiteral(long value) {
        return "0x" + Long.toHexString(value) + "L";
    }

    void set(CodeBlock.Builder output, T value) {
        int offset = offsets.get(value);
        output.addStatement("$N |= $L", maskVariable(offset), mask(offset));
    }

    void and(CodeBlock.Builder output, Collection<T> values) {
        long[] masks = toMasks(values);
        for (int i = 0; i < masks.length; i++) {
            output.addStatement("$N &= $L", maskVariables.get(i), toHexLiteral(masks[i]));
        }
    }

    CodeBlock isSet(T value) {
        int offset = offsets.get(value);
        return CodeBlock.of("($N & $L) != 0", maskVariable(offset), mask(offset));
    }

    CodeBlock allSet(Collection<T> values) {
        return multiSet0(values, true);
    }

    CodeBlock anySet(Collection<T> values) {
        return multiSet0(values, false);
    }

    private CodeBlock multiSet0(Collection<T> values, boolean all) {
        long[] expected = toMasks(values);
        CodeBlock.Builder builder = CodeBlock.builder();
        boolean first = true;
        for (int i = 0; i < expected.length; i++) {
            long value = expected[i];
            if (value != 0) {
                if (!first) {
                    builder.add(all ? " && " : " || ");
                }
                first = false;
                String valueLiteral = toHexLiteral(value);
                builder.add("($N & $L)", maskVariables.get(i), valueLiteral);
                if (all) {
                    builder.add(" == $L", valueLiteral);
                } else {
                    builder.add(" != 0");
                }
            }
        }
        return builder.build();
    }

    private long[] toMasks(Collection<T> values) {
        BitSet collected = new BitSet();
        for (T value : values) {
            collected.set(offsets.get(value));
        }
        return collected.toLongArray();
    }

    /**
     * Emit code that checks that the required values are set.
     *
     * @param requiredValues The required values, mapped to the code block of what action to take when a value is
     *                       missing.
     */
    void onMissing(CodeBlock.Builder builder, Map<T, CodeBlock> requiredValues) {
        if (requiredValues.isEmpty()) {
            return;
        }

        builder.beginControlFlow("if (!($L))", allSet(requiredValues.keySet()));

        // if there are missing variables, determine which ones
        for (Map.Entry<T, CodeBlock> entry : requiredValues.entrySet()) {
            int offset = offsets.get(entry.getKey());
            builder.beginControlFlow("if (($N & $L) == 0)", maskVariable(offset), mask(offset));
            builder.add(entry.getValue());
            builder.endControlFlow();
        }

        builder.endControlFlow();
    }

    CodeBlock bitCount() {
        CodeBlock.Builder builder = CodeBlock.builder();
        boolean first = true;
        for (String maskVariable : maskVariables) {
            if (!first) {
                builder.add(" + ");
            }
            first = false;
            builder.add("$T.bitCount($N)", Long.class, maskVariable);
        }
        return builder.build();
    }
}
