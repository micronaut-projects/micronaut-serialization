package io.micronaut.serde.jackson.mixin;

import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(
    value = Request.class,
    mixin = RequestMixin.class
)
@SerdeImport(
    value = Message.class,
    mixin = MessageMixin.class
)
class AppImport {}
