package io.micronaut.serde.xml;

import io.micronaut.serde.xml.bean.CustomBean;
import io.micronaut.serde.xml.bean.NestedList;
import io.micronaut.serde.xml.bean.ObjectBean;
import io.micronaut.serde.xml.bean.SimpleBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@MicronautTest
public class testEncoding {

    @Inject
    @Named("xml")
    XmlObjectMapper xmlMapper;

    @Test
    void testSimpleBean() throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SimpleBean bean = new SimpleBean(21, "Hamza");
        xmlMapper.writeValue(out, bean);
        String expectedXml = "<SimpleBean><age>21</age><name>Hamza</name></SimpleBean>";
        String actual = out.toString();
        assertEquals(expectedXml, actual);

    }

    @Test
    void testObjetBean() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SimpleBean simpleBean = new SimpleBean(21, "Hamza");
        ObjectBean bean = new ObjectBean(simpleBean);
        xmlMapper.writeValue(out, bean);
        String expectedXml = "<ObjectBean><simpleBeans><SimpleBean><age>21</age><name>Hamza</name></SimpleBean></simpleBeans></ObjectBean>";
        String actual = out.toString();
        assertEquals(expectedXml, actual);
    }

    @Test
    void customBean() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CustomBean bean = new CustomBean("A1", List.of("B1", "B2"), "A2");
        xmlMapper.writeValue(out, bean);
        String expectedXml = "<CustomBean><a1>A1</a1><c1><c1>B1</c1><c1>B2</c1></c1><b1>A2</b1></CustomBean>";
        String actual = out.toString();
        assertEquals(expectedXml, actual);
    }

    @Test
    void testNestedList() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SimpleBean bean = new SimpleBean(21, "Hamza");
        NestedList nestedList = new NestedList(List.of(bean));
        xmlMapper.writeValue(out, nestedList);
        String expectedXml = "<NestedList><nestedLists><SimpleBean><age>21</age><name>Hamza</name></SimpleBean></nestedLists></NestedList>";
        String actual = out.toString();
        assertEquals(expectedXml, actual);
    }


}
