package io.micronaut.serde.xml.annotation;

import io.micronaut.core.annotation.Introspected;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the XML root element name for a serde type.
 *
 * @author Mousrij Hamza
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Introspected
public @interface XmlRootName {

    /**
     * @return The root element name.
     */
    String value();
}
