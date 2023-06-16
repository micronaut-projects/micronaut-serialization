package io.micronaut.serde.support.deserializers;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.beans.BeanIntrospection;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Requires(property = "deser.callback", value = "true")
@Singleton
public class DeserCallback implements SerdeDeserializationPreInstantiateCallback {

    List<BeanIntrospection> visited = new ArrayList<>();

    @Override
    public void preInstantiate(BeanIntrospection<?> beanIntrospection, Object... arguments) {
        visited.add(beanIntrospection);
    }
}
