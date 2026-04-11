package io.micronaut.serde.xml.test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.serde.xml.XmlObjectMapper;
import io.micronaut.serde.xml.annotation.ObjectBean;
import io.micronaut.serde.xml.annotation.SimpleBean;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class testObjectBean {

    public static void main(String[] args) throws IOException {
        try (ApplicationContext applicationContext = ApplicationContext.run(args)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            XmlObjectMapper xmlMapper = applicationContext.getBean(XmlObjectMapper.class);
            SimpleBean simpleBean = new SimpleBean(21, "Hamza");
            int [] i = new int[] {1, 2, 3};
            ObjectBean bean = new ObjectBean( i, simpleBean, "Hamza");

            xmlMapper.writeValue(out, bean);

            System.out.println(new String(out.toByteArray()));

            System.out.println("====");

        }

    }
}
