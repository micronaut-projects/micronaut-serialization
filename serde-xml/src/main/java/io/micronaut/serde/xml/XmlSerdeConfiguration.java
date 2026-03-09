package io.micronaut.serde.xml;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

/**
 * Configuration Properties for XML serialization/deserialization.
 *
 * @author Mousrij Hamza
 */
@ConfigurationProperties("mirconaut.serde.xml")
@Internal
public final class XmlSerdeConfiguration {

    @Nullable
    private String defaultRootName;

    @Nullable
    public String getDefaultRootName() {
        return this.defaultRootName;
    }

    public void setDefaultRootName(String defaultRootName) {
        this.defaultRootName = defaultRootName;
    }

}
