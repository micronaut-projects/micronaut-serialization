package io.micronaut.serde.xml.serde;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;

@Factory
@Internal
public class FactoryXmlSerde {

    @Bean
    XmlPropertySerde xmlSerde(){
        return new XmlPropertySerde();
    }

}
