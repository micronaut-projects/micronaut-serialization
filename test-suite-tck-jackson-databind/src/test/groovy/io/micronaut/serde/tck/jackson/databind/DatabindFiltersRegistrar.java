package io.micronaut.serde.tck.jackson.databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;

@Context
@Singleton
public class DatabindFiltersRegistrar {

    @PostConstruct
    public void init(ObjectMapper objectMapper, DatabindPredicateFilter predicateFilter) {
        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.addFilter("ignore-value", predicateFilter);
        objectMapper.setFilterProvider(filterProvider);
    }

}
