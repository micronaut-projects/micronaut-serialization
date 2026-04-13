package io.micronaut.serde.xml;


import com.fasterxml.jackson.annotation.JsonRootName;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jdk.jfr.Name;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

@MicronautTest
public class testSimpleBean {
    @Inject
    @Name("xml")
    XmlObjectMapper mapper;

    @Test
    void test() throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SimpleBean simpleBean0 = new SimpleBean("1", "2", null);
        simpleBean0.setData(List.of("data"));
        SimpleBean simpleBean = new SimpleBean("A", "B", simpleBean0);

        InternalBean internalBean = new InternalBean("1A", "1B");
        simpleBean.setInternalBean(List.of(internalBean));
        mapper.writeValue(out, simpleBean);

        System.out.println(new String(out.toByteArray()));

        assert 1==1;

    }

    @Serdeable

    static class  SimpleBean {

        String ABC;
        String BCD;
        SimpleBean test;
        @JacksonXmlElementWrapper(localName = "+++")
        List<InternalBean> InternalBean;

        List<String> data;


        public SimpleBean() {
        }

        public SimpleBean(String ABC, String BCD, SimpleBean test) {
            this.ABC = ABC;
            this.BCD = BCD;
            this.test = test;

        }

        public String getABC() {
            return ABC;
        }

        public void setABC(String ABC) {
            this.ABC = ABC;
        }

        public String getBCD() {
            return BCD;
        }

        public void setBCD(String BCD) {
            this.BCD = BCD;
        }

        public List<InternalBean> getInternalBean() {
            return InternalBean;
        }

        public void setInternalBean(List<InternalBean> internalBean) {
            InternalBean = internalBean;
        }

        public SimpleBean getTest() {
            return test;
        }
        public void setTest(SimpleBean test) {}

        public List<String> getData() {
            return data;
        }

        public void setData(List<String> data) {
            this.data = data;
        }
    }
    @Serdeable
    static class InternalBean {
        String AA;
        String BB;
        public InternalBean() {

        }

        public InternalBean(String AA, String BB) {
            this.AA = AA;
            this.BB = BB;
        }
        public String getAA() {
            return AA;
        }
        public void setAA(String AA) {
            this.AA = AA;
        }
        public String getBB() {
            return BB;
        }
        public void setBB(String BB) {
            this.BB = BB;
        }
    }
}
