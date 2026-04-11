package io.micronaut.serde.xml.test;


import io.micronaut.context.ApplicationContext;
import io.micronaut.serde.xml.XmlObjectMapper;
import io.micronaut.serde.xml.annotation.CustomBean;
import io.micronaut.serde.xml.testIntrospection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;


public class testAllahAllah {

    public static void main(String[] args) {
        try (ApplicationContext applicationContext = ApplicationContext.run(args)) {
            XmlObjectMapper mapper = applicationContext.getBean(XmlObjectMapper.class);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            CustomBean bean = new CustomBean("A1", List.of("B1", "B2"), "A2");
            testIntrospection test = new testIntrospection("hamza", List.of("data", 123), "123");
            try {

                mapper.writeValue(out, bean);
                System.out.println(new String(out.toByteArray()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println("====");

        }

    }
}
