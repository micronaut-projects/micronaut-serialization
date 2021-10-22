package io.micronaut.serde.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.micronaut.core.annotation.Internal;

/**
 * Meta-annotation with meta annotation members that different annotation
 * models can be bind to.
 *
 * <p>This annotation shouldn't be used directly instead a concrete annotation
 * API for JSON like JSON-B or Jackson annotations should be used.</p>
 */
@Internal
@Retention(RetentionPolicy.RUNTIME)
public @interface SerdeConfig {
    /**
     * The property to use.
     */
    String PROPERTY = "property";

    /**
     * Is it ignored.
     */
    String IGNORED = "ignored";

    /**
     * Is this property to be used only for reading.
     */
    String READ_ONLY = "readOnly";
}
