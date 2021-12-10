package io.micronaut.serde.awslambdaevents;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeImport;

@Introspected(classes = {
        APIGatewayProxyRequestEvent.class,
        APIGatewayProxyRequestEvent.ProxyRequestContext.class,
        APIGatewayProxyRequestEvent.RequestIdentity.class
})
@SerdeImport(APIGatewayProxyRequestEvent.class)
final class APIGatewayProxyRequestEventSerde {
}
