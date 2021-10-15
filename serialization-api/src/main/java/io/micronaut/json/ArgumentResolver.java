package io.micronaut.json;

import java.util.function.Function;

import io.micronaut.core.type.Argument;

public interface ArgumentResolver extends
                                  Function<String, Argument<?>> {
}
