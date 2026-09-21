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
package io.micronaut.serde.patch;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Resource and publication policy for JSON Patch. Limits apply to one invocation. Parser scalar
 * allocations and the final Java object are additional to the replay memory budget.
 *
 * @param memoryLimit Maximum combined replay-buffer capacity in bytes
 * @param storageLimit Maximum combined live replay data in bytes, including spilled data
 * @param maxOperations Maximum operation count
 * @param maxPatchCharacters Maximum decoded patch token characters (including token overhead)
 * @param spillDirectory Existing temporary directory, or null to disable disk spill
 * @param validateBeforeWrite Whether to finish patch validation before writing output
 * @since 3.2.0
 */
@Experimental
public record JsonPatchOptions(long memoryLimit, long storageLimit, int maxOperations,
                               int maxPatchCharacters, @Nullable Path spillDirectory,
                               boolean validateBeforeWrite) {
    /**
     * Defaults: 1 MiB replay memory, 256 MiB live storage, 1,000 operations, 1 MiB patch characters,
     * no disk spill, and validation before output.
     * @since 3.2.0
     */
    public static final JsonPatchOptions DEFAULT = new JsonPatchOptions(1 << 20, 256L << 20, 1000, 1 << 20, null, true);

    /**
     * Validates resource limits.
     * @param memoryLimit Replay memory budget
     * @param storageLimit Live replay storage budget
     * @param maxOperations Operation limit
     * @param maxPatchCharacters Patch size limit
     * @param spillDirectory Optional spill directory
     * @param validateBeforeWrite Output publication policy
     * @since 3.2.0
     */
    public JsonPatchOptions {
        if (memoryLimit < 0 || storageLimit < 1 || maxOperations < 0 || maxPatchCharacters < 1) {
            throw new IllegalArgumentException("Invalid JSON Patch resource limits");
        }
    }

    /**
     * Enables spill into the supplied existing directory.
     * @param directory Temporary directory
     * @return Updated options
     * @since 3.2.0
     */
    public JsonPatchOptions withSpillDirectory(Path directory) {
        return new JsonPatchOptions(memoryLimit, storageLimit, maxOperations, maxPatchCharacters, directory, validateBeforeWrite);
    }

    /**
     * Sets whether output is delayed until all patch operations succeed. Destination I/O errors
     * can still leave partial output. Disabling validation allows late patch errors to do so too.
     * @param enabled Whether to stage output
     * @return Updated options
     * @since 3.2.0
     */
    public JsonPatchOptions withValidationBeforeWrite(boolean enabled) {
        return new JsonPatchOptions(memoryLimit, storageLimit, maxOperations, maxPatchCharacters, spillDirectory, enabled);
    }
}
