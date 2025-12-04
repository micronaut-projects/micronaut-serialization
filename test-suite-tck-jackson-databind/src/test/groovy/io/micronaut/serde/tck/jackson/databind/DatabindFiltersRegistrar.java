package io.micronaut.serde.tck.jackson.databind;

import tools.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Context;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import tools.jackson.databind.ser.std.SimpleFilterProvider;

@Context
@Singleton
public class DatabindFiltersRegistrar {

    @PostConstruct
    public void init(ObjectMapper objectMapper, DatabindPredicateFilter predicateFilter) {
        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.addFilter("ignore-value", predicateFilter);
        //TODO objectMapper.setFilterProvider(filterProvider);
    }

}
