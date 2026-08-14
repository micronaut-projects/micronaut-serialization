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
package io.micronaut.serde.jaxb.tck;

import jakarta.xml.bind.annotation.XmlAccessOrder;
import jakarta.xml.bind.annotation.XmlAccessorOrder;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlElementRefs;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlValue;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.xmlunit.builder.DiffBuilder;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared JAXB annotation compatibility scenarios.
 *
 * @since 3.2
 */
public abstract class AbstractJaxbTckTest {

    protected abstract String writeXml(Object value) throws Exception;

    protected abstract <T> T readXml(String xml, Class<T> type) throws Exception;

    @Test
    void rootElementAttributeTextAndInlineCollectionsRoundTrip() throws Exception {
        JaxbBook value = new JaxbBook();
        value.isbn = "978";
        value.title = "Serde";
        value.authors = List.of("Ana", "Ben");

        String xml = writeXml(value);
        JaxbBook decoded = readXml(xml, JaxbBook.class);

        assertXmlSimilar("""
            <book xmlns="urn:books" isbn="978">
                <title xmlns="">Serde</title>
                <author xmlns="">Ana</author>
                <author xmlns="">Ben</author>
            </book>
            """, xml);
        assertEquals("978", decoded.isbn);
        assertEquals("Serde", decoded.title);
        assertEquals(List.of("Ana", "Ben"), decoded.authors);
    }

    @Test
    void wrappersAndTransientPropertiesAreHonored() throws Exception {
        JaxbLibrary value = new JaxbLibrary();
        value.books = List.of("Serde");
        value.ignored = "secret";

        String xml = writeXml(value);
        JaxbLibrary decoded = readXml(xml, JaxbLibrary.class);

        assertXmlSimilar("<jaxbLibrary><books><book>Serde</book></books></jaxbLibrary>", xml);
        assertEquals(List.of("Serde"), decoded.books);
        assertNull(decoded.ignored);
    }

    @Test
    void defaultsDeriveRootAndPropertyNamesAndInlineCollections() throws Exception {
        JaxbDefaults value = new JaxbDefaults();
        value.title = "Serde";
        value.authors = List.of("Ana", "Ben");

        String xml = writeXml(value);
        JaxbDefaults decoded = readXml(xml, JaxbDefaults.class);

        assertXmlSimilar("<jaxbDefaults><title>Serde</title><authors>Ana</authors><authors>Ben</authors></jaxbDefaults>", xml);
        assertEquals("Serde", decoded.title);
        assertEquals(List.of("Ana", "Ben"), decoded.authors);
    }

    @Test
    void typeAndAlphabeticalOrdersAreApplied() throws Exception {
        JaxbTypeOrder typeOrder = new JaxbTypeOrder();
        typeOrder.first = "one";
        typeOrder.second = "two";
        typeOrder.third = "three";
        assertXmlSimilar("<jaxbTypeOrder><third>three</third><first>one</first><second>two</second></jaxbTypeOrder>", writeXml(typeOrder));

        JaxbAlphabeticalOrder alphabeticalOrder = new JaxbAlphabeticalOrder();
        alphabeticalOrder.zebra = "z";
        alphabeticalOrder.apple = "a";
        alphabeticalOrder.middle = "m";
        assertXmlSimilar("<jaxbAlphabeticalOrder><apple>a</apple><middle>m</middle><zebra>z</zebra></jaxbAlphabeticalOrder>", writeXml(alphabeticalOrder));
    }

    @Test
    void namespacesApplyToRootElementsAttributesElementsAndWrappers() throws Exception {
        JaxbNamespacedBook value = new JaxbNamespacedBook();
        value.code = "A1";
        value.chapter = "intro";
        value.chapters = List.of("one");

        String xml = writeXml(value);
        JaxbNamespacedBook decoded = readXml(xml, JaxbNamespacedBook.class);

        assertXmlSimilar("""
            <book xmlns="urn:root" xmlns:attribute="urn:attribute" attribute:code="A1">
                <chapter xmlns="urn:chapter">intro</chapter>
                <chapters xmlns="urn:chapters">
                    <chapter xmlns="">one</chapter>
                </chapters>
            </book>
            """, xml);
        assertEquals("A1", decoded.code);
        assertEquals("intro", decoded.chapter);
        assertEquals(List.of("one"), decoded.chapters);
    }

    @Test
    void xmlValueWritesDirectElementTextWithAttributes() throws Exception {
        JaxbTextValue value = new JaxbTextValue();
        value.language = "en";
        value.value = "hello";
        assertXmlSimilar("<jaxbTextValue language=\"en\">hello</jaxbTextValue>", writeXml(value));
    }

    @Test
    void enumLexicalValuesAreUsed() throws Exception {
        assertXmlSimilar("<JaxbEdition>second-edition</JaxbEdition>", writeXml(JaxbEdition.SECOND));
        assertEquals(JaxbEdition.SECOND, readXml("<JaxbEdition>second-edition</JaxbEdition>", JaxbEdition.class));
    }

    @Test
    void xmlElementDefaultValueMatchesJakartaXmlBindingBehavior() throws Exception {
        JaxbDefaultsWithValues empty = readXml("<jaxbDefaultsWithValues><name></name><count></count></jaxbDefaultsWithValues>", JaxbDefaultsWithValues.class);
        assertEquals("unknown", empty.name);
        assertEquals(7, empty.count);

        JaxbDefaultsWithValues whitespaceAndNil = readXml("<jaxbDefaultsWithValues><name> </name><count xsi:nil=\"true\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"></count></jaxbDefaultsWithValues>", JaxbDefaultsWithValues.class);
        assertEquals(" ", whitespaceAndNil.name);
        assertNull(whitespaceAndNil.count);

        JaxbDefaultsWithValues missing = readXml("<jaxbDefaultsWithValues></jaxbDefaultsWithValues>", JaxbDefaultsWithValues.class);
        assertEquals("initial", missing.name);
        assertNull(missing.count);
    }

    @Test
    void nillableElementsAndCollectionItemsUseXsiNil() throws Exception {
        JaxbNillableElements value = new JaxbNillableElements();
        value.values = Arrays.asList("one", null);

        String xml = writeXml(value);
        JaxbNillableElements decoded = readXml(xml, JaxbNillableElements.class);

        assertXmlSimilar("""
            <jaxbNillableElements xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <name xsi:nil="true"/>
                <value>one</value>
                <value xsi:nil="true"/>
            </jaxbNillableElements>
            """, xml);
        assertNull(decoded.name);
        assertEquals(Arrays.asList("one", null), decoded.values);
    }

    @Test
    void nillableWrappersDistinguishNullAndEmptyCollections() throws Exception {
        JaxbNillableWrapper nullValues = new JaxbNillableWrapper();
        assertXmlSimilar("""
            <jaxbNillableWrapper xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <values xsi:nil="true"/>
            </jaxbNillableWrapper>
            """, writeXml(nullValues));

        JaxbNillableWrapper emptyValues = new JaxbNillableWrapper();
        emptyValues.values = List.of();
        assertXmlSimilar("<jaxbNillableWrapper><values/></jaxbNillableWrapper>", writeXml(emptyValues));

        JaxbNillableWrapper decoded = readXml("<jaxbNillableWrapper><values xsi:nil=\"true\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"></values></jaxbNillableWrapper>", JaxbNillableWrapper.class);
        assertNull(decoded.values);
    }

    @Test
    void xmlElementTypeOverridesCompatiblePropertyTypes() throws Exception {
        JaxbElementType value = new JaxbElementType();
        value.value = "typed";

        String xml = writeXml(value);
        JaxbElementType decoded = readXml(xml, JaxbElementType.class);

        assertXmlSimilar("<jaxbElementType><value>typed</value></jaxbElementType>", xml);
        assertInstanceOf(String.class, decoded.value);
        assertEquals("typed", decoded.value);
    }

    @Test
    void xmlElementRefUsesTheReferencedElementName() throws Exception {
        JaxbRefContainer value = new JaxbRefContainer();
        value.pet = new JaxbDog();
        value.pet.name = "Rex";

        String xml = writeXml(value);
        JaxbRefContainer decoded = readXml(xml, JaxbRefContainer.class);

        assertXmlSimilar("<jaxbRefContainer><dog><name>Rex</name></dog></jaxbRefContainer>", xml);
        assertInstanceOf(JaxbDog.class, decoded.pet);
        assertEquals("Rex", decoded.pet.name);
    }

    @Test
    void xmlElementsAndXmlElementRefsUseTheirChoiceElementNames() throws Exception {
        JaxbElementsContainer elements = new JaxbElementsContainer();
        elements.pet = new JaxbDog();
        elements.pet.name = "Rex";
        String elementsXml = writeXml(elements);
        assertXmlSimilar("<jaxbElementsContainer><dog><name>Rex</name></dog></jaxbElementsContainer>", elementsXml);
        assertInstanceOf(JaxbDog.class, readXml(elementsXml, JaxbElementsContainer.class).pet);

        JaxbElementRefsContainer refs = new JaxbElementRefsContainer();
        refs.pet = new JaxbDog();
        refs.pet.name = "Rex";
        String refsXml = writeXml(refs);
        assertXmlSimilar("<jaxbElementRefsContainer><dog><name>Rex</name></dog></jaxbElementRefsContainer>", refsXml);
        assertInstanceOf(JaxbDog.class, readXml(refsXml, JaxbElementRefsContainer.class).pet);

        elements.pet = new JaxbCat();
        elements.pet.name = "Milo";
        assertXmlSimilar("<jaxbElementsContainer><cat><name>Milo</name></cat></jaxbElementsContainer>", writeXml(elements));

        refs.pet = new JaxbCat();
        refs.pet.name = "Milo";
        assertXmlSimilar("<jaxbElementRefsContainer><cat><name>Milo</name></cat></jaxbElementRefsContainer>", writeXml(refs));
    }
    /** JAXB root, element, attribute, text, and inline collection model. */
    @XmlRootElement(name = "book", namespace = "urn:books")
    @XmlType(propOrder = {"title", "authors"})
    public static class JaxbBook {
        @XmlAttribute(name = "isbn")
        public String isbn;

        @XmlElement(name = "title")
        public String title;

        @XmlElement(name = "author")
        public List<String> authors;

    }

    /** JAXB reference and choice model. */
    @XmlRootElement
    public static class JaxbRefContainer {
        @XmlElementRef(name = "dog", namespace = "##default", type = JaxbDog.class, required = true)
        public JaxbPet pet;
    }

    /** JAXB element-choice model. */
    @XmlRootElement
    public static class JaxbElementsContainer {
        @XmlElements({
            @XmlElement(name = "dog", namespace = "##default", type = JaxbDog.class, required = true),
            @XmlElement(name = "cat", namespace = "##default", type = JaxbCat.class)
        })
        public JaxbPet pet;
    }

    /** JAXB element-reference-choice model. */
    @XmlRootElement
    public static class JaxbElementRefsContainer {
        @XmlElementRefs({
            @XmlElementRef(name = "dog", namespace = "##default", type = JaxbDog.class, required = true),
            @XmlElementRef(name = "cat", namespace = "##default", type = JaxbCat.class)
        })
        public JaxbPet pet;
    }

    /** JAXB polymorphic base type. */
    @XmlSeeAlso({JaxbDog.class, JaxbCat.class})
    public static class JaxbPet {
        public String name;
    }

    /** JAXB polymorphic subtype. */
    @XmlRootElement(name = "dog")
    public static class JaxbDog extends JaxbPet {
    }

    /** JAXB polymorphic subtype. */
    @XmlRootElement(name = "cat")
    public static class JaxbCat extends JaxbPet {
    }

    /** JAXB wrapper and transient property model. */
    @XmlRootElement
    public static class JaxbLibrary {
        @XmlElementWrapper(name = "books")
        @XmlElement(name = "book")
        public List<String> books;

        @XmlTransient
        public String ignored;
    }

    /** JAXB default-name collection model. */
    @XmlRootElement
    public static class JaxbDefaults {
        @XmlElement
        public String title;

        @XmlElement
        public List<String> authors;
    }

    /** JAXB explicit property-order model. */
    @XmlRootElement
    @XmlType(propOrder = {"third", "first", "second"})
    public static class JaxbTypeOrder {
        public String first;
        public String second;
        public String third;
    }

    /** JAXB alphabetical property-order model. */
    @XmlRootElement
    @XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
    public static class JaxbAlphabeticalOrder {
        public String zebra;
        public String apple;
        public String middle;
    }

    /** JAXB namespace model. */
    @XmlRootElement(name = "book", namespace = "urn:root")
    public static class JaxbNamespacedBook {
        @XmlAttribute(name = "code", namespace = "urn:attribute")
        public String code;

        @XmlElement(name = "chapter", namespace = "urn:chapter")
        public String chapter;

        @XmlElementWrapper(name = "chapters", namespace = "urn:chapters")
        @XmlElement(name = "chapter")
        public List<String> chapters;
    }

    /** JAXB text-content model. */
    @XmlRootElement
    public static class JaxbTextValue {
        @XmlAttribute
        public String language;

        @XmlValue
        public String value;
    }

    /** JAXB XML default-value model. */
    @XmlRootElement
    public static class JaxbDefaultsWithValues {
        @XmlElement(defaultValue = "unknown")
        public String name = "initial";

        @XmlElement(defaultValue = "7", nillable = true)
        public @Nullable Integer count;
    }

    /** JAXB nillable element and collection item model. */
    @XmlRootElement
    public static class JaxbNillableElements {
        @XmlElement(nillable = true)
        public @Nullable String name;

        @XmlElement(nillable = false)
        public @Nullable String omitted;

        @XmlElement(name = "value", nillable = true)
        public @Nullable List<String> values;
    }

    /** JAXB nillable wrapper model. */
    @XmlRootElement
    public static class JaxbNillableWrapper {
        @XmlElementWrapper(nillable = true)
        public @Nullable List<String> values;
    }

    /** JAXB property type-override model. */
    @XmlRootElement
    public static class JaxbElementType {
        @XmlElement(type = String.class)
        public CharSequence value;
    }
    /** JAXB enum lexical-value model. */
    @XmlRootElement(name = "JaxbEdition")
    @XmlEnum
    public enum JaxbEdition {
        FIRST,
        @XmlEnumValue("second-edition")
        SECOND
    }

    private static void assertXmlSimilar(String expected, String actual) {
        var diff = DiffBuilder.compare(expected)
            .withTest(actual)
            .ignoreWhitespace()
            .checkForSimilar()
            .build();
        assertFalse(diff.hasDifferences(), diff::toString);
    }
}
