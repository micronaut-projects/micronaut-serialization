/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.micronaut.serde.xml.woodstox

import io.micronaut.serde.xml.tck.AbstractJaxbXmlAnnotationSpec
import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class WoodstoxJaxbAnnotationSpec extends AbstractJaxbXmlAnnotationSpec implements WoodstoxXmlSpec {
    @Inject
    XmlObjectMapper xmlMapper
}
