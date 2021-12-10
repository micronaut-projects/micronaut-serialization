package io.micronaut.serde.awslambdaevents;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(SQSEvent.class)
final class SQSEventSerde {
}
